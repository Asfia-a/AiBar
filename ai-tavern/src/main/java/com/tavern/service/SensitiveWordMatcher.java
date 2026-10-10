package com.tavern.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 敏感词匹配器。
 * <p>
 * 核心是<b>间隔容错匹配</b>：敏感词 {@code abcd} 被写成 {@code aXbYcd} 时仍需命中，
 * 也就是允许相邻敏感字之间插入 0～{@value #MAX_GAP} 个无关字。
 * 实现上用一次存在性 DP 扫描整句，{@code dp[j]} 记录匹配到词第 j 个字时
 * 最早可用的文本位置；取「最早位置」是因为它给后续字符留的余地最大。
 * <p>
 * 复杂度 O(文本长度 × 词长)，且单趟扫描同时验证所有词，
 * 相比逐词 contains 的 O(词库数 × 文本长度) 在词库上千条时优势明显。
 * <p>
 * 关于误报：间隔容错必然带来误报（正常词里恰好夹着敏感字，如「色情倾向」会被「色情」命中）。
 * 这里接受这个代价——它换来的是拆字与插字绕过完全失效。
 * 分级处理在 {@link SensitiveNormalizer} 之后的业务层做：低等级词只记日志不拦截。
 */
@Slf4j
@Component
public class SensitiveWordMatcher {

    /** 敏感字之间最多允许多少个无关字。2 能挡住「违.禁.词」「违abc禁def词」；调到 3 误报明显上升。 */
    private static final int MAX_GAP = 2;

    private volatile List<String> words = List.of();

    /**
     * 更新词库。词库变更时调用；整体替换不可变列表，读侧无锁。
     */
    public synchronized void build(List<String> normalizedWords) {
        List<String> list = new ArrayList<>();
        if (normalizedWords != null) {
            for (String w : normalizedWords) {
                if (StringUtils.hasText(w) && w.length() >= 2) {
                    list.add(w);
                }
            }
        }
        this.words = List.copyOf(list);
        log.info("敏感词库已加载，归一化后词条 {}", list.size());
    }

    public int wordCount() {
        return words.size();
    }

    /**
     * 在归一化文本上匹配，返回命中的词（按发现顺序去重）。
     *
     * @param normalizedText 必须已经过 {@link SensitiveNormalizer#normalize}，
     *                       词库也须用同一管线处理过
     */
    public Set<String> match(String normalizedText) {
        if (!StringUtils.hasText(normalizedText) || words.isEmpty()) {
            return Set.of();
        }
        Set<String> hits = new LinkedHashSet<>();
        List<String> dict = words;
        int n = normalizedText.length();
        int[] dp = new int[maxWordLength(dict)];
        for (String w : dict) {
            int L = w.length();
            if (L > dp.length || n < L) {
                continue;
            }
            java.util.Arrays.fill(dp, 0, L, UNREACHABLE);
            for (int i = 0; i < n; i++) {
                char c = normalizedText.charAt(i);
                // 倒序更新，保证同一个文本字符不会被词里的多个位置重复占用
                for (int j = L - 1; j >= 1; j--) {
                    if (c == w.charAt(j) && dp[j - 1] != UNREACHABLE && i - dp[j - 1] - 1 <= MAX_GAP) {
                        dp[j] = Math.min(dp[j], i);
                    }
                }
                if (c == w.charAt(0)) {
                    dp[0] = Math.min(dp[0], i);
                }
                if (dp[L - 1] != UNREACHABLE) {
                    break;
                }
            }
            if (dp[L - 1] != UNREACHABLE) {
                hits.add(w);
            }
        }
        return hits;
    }

    private static final int UNREACHABLE = Integer.MAX_VALUE;

    private static int maxWordLength(List<String> dict) {
        int max = 1;
        for (String w : dict) {
            max = Math.max(max, w.length());
        }
        return max;
    }
}
