package com.tavern.prompt;

import com.tavern.entity.WorldInfoEntry;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 世界书激活。
 *
 * <p>纯函数式的一层：给「候选条目 + 扫描文本 + 预算」，返回这一轮应该插入的条目。
 * 规则与 SillyTavern 的常用子集对齐：
 * <ol>
 *   <li>常量条目（constant）无条件先入，按 order 升序；</li>
 *   <li>关键词命中即入；keys 支持英文逗号分隔，<code>/.../flags</code> 形式按正则；</li>
 *   <li>按 order 升序、在 token 预算内贪心装入，超预算即停止；</li>
 *   <li>递归一层：已入条目的正文里提到别的条目关键词，再尝试装入；</li>
 *   <li>概率过滤：probability &lt; 100 时按概率决定是否真的插入。</li>
 * </ol>
 */
@Service
public class WorldInfoService {

    /** 递归扫描最多几轮（一层足够，避免条目互相引用导致爆炸） */
    private static final int MAX_RECURSION = 1;

    /**
     * @param entries    候选条目（已按角色/用户过滤好）
     * @param scanText   用来匹配的文本，一般是最近 N 条消息拼起来
     * @param budget     世界书这段允许占用的 token
     * @return 命中的条目，已按 order 升序排好
     */
    public List<WorldInfoEntry> activate(List<WorldInfoEntry> entries, String scanText, int budget) {
        List<WorldInfoEntry> result = new ArrayList<>();
        if (entries == null || entries.isEmpty()) {
            return result;
        }

        List<WorldInfoEntry> sorted = new ArrayList<>(entries);
        sorted.removeIf(e -> e == null || !e.isEnabled() || e.getContent() == null
                || e.getContent().isBlank());
        sorted.sort(Comparator.comparingInt(e -> e.getOrderNo() == null ? 100 : e.getOrderNo()));

        String text = scanText == null ? "" : scanText;
        Set<Long> picked = new HashSet<>();
        int used = 0;

        // 1. 常量条目优先
        for (WorldInfoEntry e : sorted) {
            if (!e.isConstant()) {
                continue;
            }
            int cost = TokenEstimator.estimate(e.getContent());
            if (used + cost > budget) {
                continue;
            }
            if (!roll(e)) {
                continue;
            }
            result.add(e);
            picked.add(e.getId());
            used += cost;
        }

        // 2. 关键词命中
        for (WorldInfoEntry e : sorted) {
            if (e.isConstant() || picked.contains(e.getId())) {
                continue;
            }
            if (!matches(e, text)) {
                continue;
            }
            int cost = TokenEstimator.estimate(e.getContent());
            if (used + cost > budget) {
                continue;
            }
            if (!roll(e)) {
                continue;
            }
            result.add(e);
            picked.add(e.getId());
            used += cost;
        }

        // 3. 递归扫描：已入条目的正文可能提到别的条目关键词
        for (int round = 0; round < MAX_RECURSION; round++) {
            StringBuilder buf = new StringBuilder(text);
            for (WorldInfoEntry e : result) {
                buf.append('\n').append(e.getContent());
            }
            boolean grew = false;
            for (WorldInfoEntry e : sorted) {
                if (e.isConstant() || picked.contains(e.getId()) || result.contains(e)) {
                    continue;
                }
                if (!matches(e, buf.toString())) {
                    continue;
                }
                int cost = TokenEstimator.estimate(e.getContent());
                if (used + cost > budget) {
                    continue;
                }
                if (!roll(e)) {
                    continue;
                }
                result.add(e);
                picked.add(e.getId());
                used += cost;
                grew = true;
            }
            if (!grew) {
                break;
            }
        }

        result.sort(Comparator.comparingInt(e -> e.getOrderNo() == null ? 100 : e.getOrderNo()));
        return result;
    }

    /** keys 用英文逗号分隔；单个 key 支持 /正则/flags 写法 */
    static boolean matches(WorldInfoEntry entry, String text) {
        List<String> keys = splitKeys(entry.getKeys());
        if (keys.isEmpty()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String raw : keys) {
            String key = raw.trim();
            if (key.isEmpty()) {
                continue;
            }
            if (isRegexKey(key)) {
                if (regexMatches(key, text)) {
                    return true;
                }
            } else if (lower.contains(key.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRegexKey(String key) {
        return key.length() > 2 && key.startsWith("/") && key.lastIndexOf('/') > 0;
    }

    private static boolean regexMatches(String expr, String text) {
        int last = expr.lastIndexOf('/');
        String body = expr.substring(1, last);
        String flags = expr.substring(last + 1);
        int f = 0;
        if (flags.contains("i")) {
            f |= Pattern.CASE_INSENSITIVE;
        }
        if (flags.contains("m")) {
            f |= Pattern.MULTILINE;
        }
        if (flags.contains("s")) {
            f |= Pattern.DOTALL;
        }
        try {
            Matcher m = Pattern.compile(body, f).matcher(text);
            return m.find();
        } catch (RuntimeException ex) {
            // 正则写错不应该让整次对话失败，当作普通文本再比一次
            return text.toLowerCase(Locale.ROOT).contains(expr.toLowerCase(Locale.ROOT));
        }
    }

    /** 逗号分隔，但要放过 /正则, 里的逗号 */
    static List<String> splitKeys(String keys) {
        List<String> out = new ArrayList<>();
        if (keys == null || keys.isBlank()) {
            return out;
        }
        StringBuilder buf = new StringBuilder();
        boolean inRegex = false;
        for (int i = 0; i < keys.length(); i++) {
            char c = keys.charAt(i);
            if (c == '/') {
                // 判断是不是正则的开头或结尾
                if (!inRegex && buf.toString().trim().isEmpty()) {
                    inRegex = true;
                } else if (inRegex) {
                    inRegex = false;
                }
                buf.append(c);
                continue;
            }
            if (c == ',' && !inRegex) {
                out.add(buf.toString());
                buf.setLength(0);
                continue;
            }
            buf.append(c);
        }
        if (!buf.toString().isBlank()) {
            out.add(buf.toString());
        }
        // 去重保序
        return new ArrayList<>(new LinkedHashSet<>(out));
    }

    private static boolean roll(WorldInfoEntry e) {
        int p = e.getProbability() == null ? 100 : e.getProbability();
        if (p >= 100) {
            return true;
        }
        if (p <= 0) {
            return false;
        }
        return Math.random() * 100 < p;
    }
}
