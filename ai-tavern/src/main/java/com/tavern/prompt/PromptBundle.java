package com.tavern.prompt;

import java.util.List;
import java.util.Map;

/**
 * 提示词构建结果 + 统计。
 *
 * <p>统计值会同时喂给两处：前端「上下文占用条」和运行指标里的趋势图，
 * 所以这里把各段的字符数分开记，而不是只给一个总数。
 */
public record PromptBundle(
        List<Map<String, String>> messages,
        /** 取进来参与拼接的历史消息数 */
        int window,
        /** 实际送进模型的历史消息数 */
        int usedRounds,
        /** 因预算被丢弃的消息数 */
        int droppedRounds,
        int systemChars,
        int dialogueChars,
        int worldInfoChars,
        int authorNoteChars,
        /** 本轮命中的世界书条目标题，前端可展示「这次用了哪些设定」 */
        List<String> activatedEntries
) {

    public int estimatedTokens() {
        return (int) ((systemChars + dialogueChars + worldInfoChars + authorNoteChars)
                / TokenEstimator.CHARS_PER_TOKEN);
    }

    /** 调用方知道会话总消息数时，用这个修正被丢弃的条数 */
    public PromptBundle withDroppedRounds(int dropped) {
        return new PromptBundle(messages, window, usedRounds, dropped, systemChars,
                dialogueChars, worldInfoChars, authorNoteChars, activatedEntries);
    }
}
