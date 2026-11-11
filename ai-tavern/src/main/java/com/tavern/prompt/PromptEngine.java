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
 * 提示词引擎：把「角色卡 + 用户人设 + 记忆摘要 + 历史 + 世界书 + 作者注释」拼成 messages。
 *
 * <p>拼装顺序对齐 SillyTavern：
 * <pre>
 * system  : 主提示（含角色设定）
 * system  : 世界书（before_char / after_char）
 * system  : 用户人设（关于 {{user}}）
 * system  : 剧情摘要（被窗口裁掉的早期内容）
 * [history]
 *   └─ in_chat 注入：世界书 / 作者注释按 depth 插到倒数第 N 条之前
 * system  : Post-History Instructions（收尾强指令，永远在最后）
 * </pre>
 */
@Service
@RequiredArgsConstructor
public class PromptEngine {

    /** 世界书最多占总输入预算的两成半 */
    private static final double WORLD_INFO_RATIO = 0.25;
    /** 历史对话至少占输入预算的比例，避免长角色卡把历史挤成 0 条 */
    private static final double HISTORY_FLOOR_RATIO = 0.3;
    /** 历史保底的绝对下限（token） */
    private static final int MIN_HISTORY_TOKENS = 800;

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

        // 2.5 用户人设：角色设定之后、历史之前——模型要先知道「对面是谁」
        String personaBlock = "";
        if (in.userPersona() != null && !in.userPersona().isBlank()) {
            personaBlock = templates.render(templates.get("user_persona_format"),
                    Map.of("user", userName, "content", in.userPersona().trim())).trim();
        }

        // 2.6 剧情摘要：紧贴历史之前，补上被窗口裁掉的早期事实
        String memoryBlock = "";
        if (in.memorySummary() != null && !in.memorySummary().isBlank()) {
            memoryBlock = templates.render(templates.get("memory_summary_format"),
                    Map.of("content", in.memorySummary().trim())).trim();
        }

        // 3. 作者注释：按频率决定这轮插不插；depth=0 表示挂在历史末尾（离模型最近）
        String authorNote = "";
        Injection authorInjection = null;
        if (isAuthorNoteDue(in)) {
            authorNote = templates.render(templates.get("author_note_format"),
                    Map.of("content", in.authorNote().trim())).trim();
            authorInjection = new Injection(Math.max(in.authorNoteDepth(), 0), "system", authorNote);
        }

        // 3.5 Post-History Instructions：历史之后追加的最后一条 system
        String postHistory = "";
        if (in.postHistory() != null && !in.postHistory().isBlank()) {
            postHistory = templates.render(templates.get("post_history_format"),
                    Map.of("content", in.postHistory().trim())).trim();
        }

        int wiChars = beforeChar.stream().mapToInt(String::length).sum()
                + afterChar.stream().mapToInt(String::length).sum()
                + inChat.stream().filter(i -> i.content() != null)
                .mapToInt(i -> i.content().length()).sum();
        int anChars = authorInjection == null ? 0 : authorNote.length();
        int personaChars = personaBlock.length();
        int memoryChars = memoryBlock.length();

        // 4. 预算：固定部分先占，剩下给历史
        int fixedTokens = TokenEstimator.estimate(system)
                + (beforeChar.isEmpty() && afterChar.isEmpty() && inChat.isEmpty() ? 0 : wiChars / 3)
                + TokenEstimator.estimate(authorNote)
                + TokenEstimator.estimate(postHistory)
                + TokenEstimator.estimate(personaBlock)
                + TokenEstimator.estimate(memoryBlock);

        // 历史必须保底：角色卡写得很细时，主提示可能吃掉大半预算。
        // 如果只是「剩下的给历史」，长卡会让历史直接变成 0 条，
        // 模型看不到对话，就会回出「How can I help you?」这种跳出人设的话。
        int historyFloor = Math.max(MIN_HISTORY_TOKENS, (int) (in.maxInputTokens() * HISTORY_FLOOR_RATIO));
        // 注意：maxOutputTokens 是输出预留，不占输入预算，不能在这里相减
        int historyBudget = Math.max(in.maxInputTokens() - fixedTokens, historyFloor);

        List<PromptInput.Turn> kept = trimHistory(in.history(), historyBudget);

        // 5. 组装 messages
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(msg("system", system));
        beforeChar.forEach(c -> messages.add(msg("system", c)));
        afterChar.forEach(c -> messages.add(msg("system", c)));
        if (!personaBlock.isEmpty()) {
            messages.add(msg("system", personaBlock));
        }
        if (!memoryBlock.isEmpty()) {
            messages.add(msg("system", memoryBlock));
        }

        // in-chat 注入：按 depth 从大到小（越靠前）依次插入，保证下标不漂移
        List<Injection> sorted = new ArrayList<>(inChat);
        if (authorInjection != null) {
            sorted.add(authorInjection);
        }
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
        // 收尾指令永远排最后：历史可以因预算被裁，这条不能被历史挤走
        if (!postHistory.isEmpty()) {
            messages.add(msg("system", postHistory));
        }

        List<String> titles = activated.stream()
                .map(e -> nvl(e.getTitle(), String.valueOf(e.getId())))
                .toList();

        return new PromptBundle(messages, in.history().size(), kept.size(),
                Math.max(in.history().size() - kept.size(), 0),
                system.length() + beforeChar.stream().mapToInt(String::length).sum()
                        + afterChar.stream().mapToInt(String::length).sum()
                        + postHistory.length(),
                dialogueChars, wiChars, anChars, personaChars, memoryChars, titles);
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
        if (in.userPersona() != null) {
            sb.append(in.userPersona());
        }
        if (in.memorySummary() != null) {
            sb.append(in.memorySummary());
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
