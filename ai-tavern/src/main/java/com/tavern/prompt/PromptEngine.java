package com.tavern.prompt;

import com.tavern.entity.WorldInfoEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 提示词引擎：把「角色卡 + 历史 + 世界书 + 作者注释」拼成一次请求的 messages。
 *
 * <p>拼装顺序对齐 SillyTavern：
 * <pre>
 * system  : 主提示（含角色设定）
 * system  : 世界书（before_char）
 * system  : 作者注释（after_char）
 * [history]
 *   └─ in_chat 注入：世界书 / 作者注释按 depth 插到倒数第 N 条之前
 * </pre>
 * 越靠后的内容对模型这次回复影响越大，所以作者注释默认放在历史附近而不是最前面。
 */
@Service
@RequiredArgsConstructor
public class PromptEngine {

    /** 世界书最多占总输入预算的两成半 */
    private static final double WORLD_INFO_RATIO = 0.25;
    /** 作者注释占比很小，但保留一个下限，避免被挤没 */
    private static final int AUTHOR_NOTE_MIN_TOKENS = 120;

    private final PromptTemplateService templates;
    private final WorldInfoService worldInfoService;

    public PromptBundle build(PromptInput in) {
        String charName = nvl(in.charName(), "角色");
        String userName = nvl(in.userName(), "你");

        // 1. 角色设定块 → 主提示
        Map<String, String> vars = PromptTemplateService.vars(charName, userName);
        vars.put("description", nvl(in.description(), "（未填写）"));
        vars.put("personality", nvl(in.personality(), "（未填写）"));
        vars.put("background", nvl(in.background(), "（未填写）"));
        vars.put("example", nvl(in.exampleDialogue(), ""));
        vars.put("tags", nvl(in.tags(), ""));
        String characterBlock = templates.render(templates.get("character"), vars).trim();
        vars.put("persona_block", characterBlock);
        String system = templates.render(templates.get("main"), vars).trim();

        // 2. 世界书：只把命中的条目塞进来
        int wiBudget = (int) (in.maxInputTokens() * WORLD_INFO_RATIO);
        String scanText = buildScanText(in);
        List<WorldInfoEntry> activated = worldInfoService.activate(in.worldEntries(), scanText, wiBudget);

        List<String> beforeChar = new ArrayList<>();
        List<String> afterChar = new ArrayList<>();
        List<Injection> inChat = new ArrayList<>();
        for (WorldInfoEntry e : activated) {
            String content = templates.render(templates.get("worldinfo_format"),
                    Map.of("title", nvl(e.getTitle(), "设定"), "content", nvl(e.getContent(), "")));
            String pos = e.getPosition() == null ? "after_char" : e.getPosition();
            switch (pos) {
                case "before_char" -> beforeChar.add(content);
                case "in_chat" -> inChat.add(new Injection(
                        e.getDepth() == null ? 4 : e.getDepth(), "system", content));
                default -> afterChar.add(content);
            }
        }

        // 3. 作者注释：按频率决定这轮插不插
        String authorNote = "";
        if (isAuthorNoteDue(in)) {
            authorNote = templates.render(templates.get("author_note_format"),
                    Map.of("content", in.authorNote().trim())).trim();
            if (in.authorNoteDepth() > 0) {
                inChat.add(new Injection(in.authorNoteDepth(), "system", authorNote));
            }
        }

        int wiChars = beforeChar.stream().mapToInt(String::length).sum()
                + afterChar.stream().mapToInt(String::length).sum()
                + inChat.stream().filter(i -> i.content() != null)
                .mapToInt(i -> i.content().length()).sum();
        int anChars = inChat.stream()
                .filter(i -> i.content() != null && i.content().startsWith("【作者注释】"))
                .mapToInt(i -> i.content().length()).sum();

        // 4. 预算：固定部分（主提示 + 世界书 + 注释）先占，剩下给历史
        int fixedTokens = TokenEstimator.estimate(system)
                + (beforeChar.isEmpty() && afterChar.isEmpty() && inChat.isEmpty() ? 0 : wiChars / 3)
                + TokenEstimator.estimate(authorNote);
        int remaining = Math.max(in.maxInputTokens() - fixedTokens,
                AUTHOR_NOTE_MIN_TOKENS);
        int historyBudget = Math.max(remaining - in.maxOutputTokens(), 0);

        List<PromptInput.Turn> kept = trimHistory(in.history(), historyBudget);

        // 5. 组装 messages
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(msg("system", system));
        beforeChar.forEach(c -> messages.add(msg("system", c)));
        afterChar.forEach(c -> messages.add(msg("system", c)));

        // in-chat 注入：按 depth 从大到小（越靠前）依次插入，保证下标不漂移
        List<Injection> sorted = new ArrayList<>(inChat);
        sorted.sort(Comparator.comparingInt(Injection::depth).reversed());
        List<PromptInput.Turn> tail = new ArrayList<>(kept);
        for (Injection inj : sorted) {
            int pos = Math.max(0, Math.min(tail.size(), tail.size() - inj.depth()));
            tail.add(pos, new PromptInput.Turn(inj.role(), inj.content()));
        }

        int dialogueChars = 0;
        for (PromptInput.Turn t : tail) {
            messages.add(msg(t.role(), t.content()));
            dialogueChars += t.content() == null ? 0 : t.content().length();
        }

        List<String> titles = activated.stream()
                .map(e -> nvl(e.getTitle(), String.valueOf(e.getId())))
                .toList();

        return new PromptBundle(messages, in.history().size(), kept.size(),
                Math.max(in.history().size() - kept.size(), 0),
                system.length() + beforeChar.stream().mapToInt(String::length).sum()
                        + afterChar.stream().mapToInt(String::length).sum(),
                dialogueChars, wiChars, anChars, titles);
    }

    /** 从最新往回装历史，装不下就停——先保最近的对话 */
    private List<PromptInput.Turn> trimHistory(List<PromptInput.Turn> history, int budget) {
        List<PromptInput.Turn> kept = new ArrayList<>();
        if (history == null || history.isEmpty()) {
            return kept;
        }
        int used = 0;
        for (int i = history.size() - 1; i >= 0; i--) {
            PromptInput.Turn t = history.get(i);
            int cost = TokenEstimator.estimate(t.content());
            if (used + cost > budget) {
                break;
            }
            kept.add(0, t);
            used += cost;
        }
        return kept;
    }

    private String buildScanText(PromptInput in) {
        int depth = Math.max(1, in.scanDepth());
        List<PromptInput.Turn> h = in.history();
        StringBuilder sb = new StringBuilder();
        int from = Math.max(0, h.size() - depth);
        for (int i = from; i < h.size(); i++) {
            sb.append(h.get(i).content()).append('\n');
        }
        // 用户这轮刚说的话还没入库时也能命中世界书
        if (in.authorNote() != null) {
            sb.append(in.authorNote());
        }
        return sb.toString();
    }

    private boolean isAuthorNoteDue(PromptInput in) {
        if (in.authorNote() == null || in.authorNote().isBlank()) {
            return false;
        }
        int freq = in.authorNoteFrequency();
        if (freq <= 0) {
            return false;
        }
        return in.userTurnCount() % freq == 0;
    }

    private static Map<String, String> msg(String role, String content) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("role", role);
        m.put("content", content == null ? "" : content);
        return m;
    }

    private static String nvl(String v, String fallback) {
        return v == null || v.isBlank() ? fallback : v;
    }

    private record Injection(int depth, String role, String content) {
    }
}
