package com.tavern.service;

import com.tavern.entity.ChatMessage;
import com.tavern.prompt.PromptTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 长对话记忆摘要。
 *
 * <p>当早期消息滚出上下文窗口时，把它们压成一段摘要再塞回提示词，
 * 避免角色「失忆」。核心压缩逻辑拆成静态方法，方便不连模型的单测。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemorySummaryService {

    /** 摘要正文硬上限，避免摘要本身把预算吃光 */
    public static final int MAX_SUMMARY_CHARS = 1200;
    /** 单次送去压缩的对话文本上限 */
    public static final int MAX_TRANSCRIPT_CHARS = 6000;
    /** 一次最多纳入摘要的消息条数，防止极端长会话一次打爆 */
    public static final int MAX_BATCH_MESSAGES = 80;

    private final AiModelService aiModelService;
    private final PromptTemplateService templates;

    /**
     * 是否需要刷新：有尚未覆盖、且位于窗口之前的消息。
     *
     * @param memoryUntilId 已覆盖到的消息 id（含），null 表示从未摘要
     * @param oldestInWindowId 当前窗口里最早一条消息的 id；null 表示窗口为空
     * @param uncoveredCount 落在 (memoryUntilId, oldestInWindowId) 的消息数
     */
    public static boolean needsRefresh(Long memoryUntilId, Long oldestInWindowId, int uncoveredCount) {
        if (uncoveredCount <= 0) {
            return false;
        }
        // 窗口还是空的（极少见）：只要有未覆盖消息就值得压
        if (oldestInWindowId == null) {
            return true;
        }
        // 已覆盖点已经追到窗口边缘，无需再压
        return memoryUntilId == null || memoryUntilId < oldestInWindowId - 1;
    }

    /** 把消息列表压成可读对白文本，超长从头部裁（保留较新的） */
    public static String buildTranscript(List<ChatMessage> messages, int maxChars) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        for (ChatMessage m : messages) {
            if (m == null || m.getContent() == null || m.getContent().isBlank()) {
                continue;
            }
            String role = m.getRole() == null ? "?" : m.getRole();
            String label = "assistant".equalsIgnoreCase(role) ? "角色"
                    : "user".equalsIgnoreCase(role) ? "用户" : role;
            lines.add(label + "：" + m.getContent().trim());
        }
        if (lines.isEmpty()) {
            return "";
        }
        // 从尾往头拼，保证超长时留住较新的内容
        StringBuilder sb = new StringBuilder();
        for (int i = lines.size() - 1; i >= 0; i--) {
            String line = lines.get(i);
            int add = line.length() + (sb.isEmpty() ? 0 : 1);
            if (sb.length() + add > maxChars) {
                break;
            }
            if (!sb.isEmpty()) {
                sb.insert(0, '\n');
            }
            sb.insert(0, line);
        }
        return sb.toString();
    }

    /** 无模型时的兜底：截断拼接，保证摘要链路不断 */
    public static String fallbackCompress(String oldSummary, String transcript, int maxChars) {
        String old = oldSummary == null ? "" : oldSummary.trim();
        String neu = transcript == null ? "" : transcript.trim();
        if (old.isEmpty() && neu.isEmpty()) {
            return "";
        }
        String merged;
        if (old.isEmpty()) {
            merged = neu;
        } else if (neu.isEmpty()) {
            merged = old;
        } else {
            merged = old + "\n——\n" + neu;
        }
        if (merged.length() <= maxChars) {
            return merged;
        }
        // 超长时保尾（较新）
        return merged.substring(merged.length() - maxChars);
    }

    /**
     * 合并旧摘要与新增对话。优先调模型；失败或无 Key 时走 {@link #fallbackCompress}。
     */
    public String compress(String oldSummary, List<ChatMessage> newMessages,
                           String charName, String userName) {
        List<ChatMessage> batch = newMessages == null ? List.of() : newMessages;
        if (batch.size() > MAX_BATCH_MESSAGES) {
            batch = batch.subList(batch.size() - MAX_BATCH_MESSAGES, batch.size());
        }
        String transcript = buildTranscript(batch, MAX_TRANSCRIPT_CHARS);
        if (transcript.isBlank() && (oldSummary == null || oldSummary.isBlank())) {
            return "";
        }
        if (transcript.isBlank()) {
            return clamp(oldSummary, MAX_SUMMARY_CHARS);
        }

        try {
            Map<String, String> vars = PromptTemplateService.vars(charName, userName);
            vars.put("max_chars", String.valueOf(MAX_SUMMARY_CHARS));
            vars.put("old_summary", (oldSummary == null || oldSummary.isBlank()) ? "（无）" : oldSummary.trim());
            vars.put("new_dialogue", transcript);
            String prompt = templates.render(templates.get("memory_summarize"), vars).trim();
            List<Map<String, String>> messages = List.of(
                    Map.of("role", "system", "content", "你只输出剧情摘要正文。"),
                    Map.of("role", "user", "content", prompt)
            );
            String out = aiModelService.chatCompletion(messages, 0.3, 800);
            if (out != null && !out.isBlank()) {
                return clamp(stripFence(out.trim()), MAX_SUMMARY_CHARS);
            }
        } catch (Exception e) {
            log.warn("记忆摘要模型调用失败，改用本地兜底: {}", e.getMessage());
        }
        return fallbackCompress(oldSummary, transcript, MAX_SUMMARY_CHARS);
    }

    private static String clamp(String text, int max) {
        if (text == null) {
            return "";
        }
        String t = text.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }

    /** 剥掉模型偶发包的 markdown 围栏 */
    static String stripFence(String text) {
        if (text == null) {
            return "";
        }
        String t = text.trim();
        if (t.startsWith("```")) {
            int nl = t.indexOf('\n');
            if (nl > 0) {
                t = t.substring(nl + 1);
            }
            if (t.endsWith("```")) {
                t = t.substring(0, t.length() - 3);
            }
            return t.trim();
        }
        return t;
    }
}
