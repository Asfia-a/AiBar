package com.tavern.service;

import com.tavern.common.BusinessException;
import com.tavern.common.ErrorCode;
import com.tavern.entity.ChatMessage;
import com.tavern.entity.ChatSession;
import com.tavern.entity.CharacterCard;
import com.tavern.mapper.CharacterMapper;
import com.tavern.mapper.ChatMessageMapper;
import com.tavern.mapper.ChatSessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 叙事生成：把一场角色扮演对话的完整记录改写成小说 / 剧本 / 大纲。
 * <p>
 * 与 {@link ChatService} 的区别在于输入源与输出形态：
 * 聊天是「单条消息 → 单条回复」，叙事是「整场会话 → 一篇完整作品」，
 * 因此提示词采用分层结构 —— 角色设定、剧情骨架、对话原文、写作要求四段，
 * 对话原文按 token 预算做分层裁剪，保证长会话也不会超出模型上下文。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NarrativePromptService {

    private final ChatMessageMapper messageMapper;
    private final ChatSessionMapper sessionMapper;
    private final CharacterMapper characterMapper;

    /** 单次叙事允许的最大对话原文 token 估算值（中文约 1 字 ≈ 0.7 token，留出输出空间） */
    private static final int DIALOGUE_TOKEN_BUDGET = 4000;

    public record Request(Long sessionId, String formType, String style, String length,
                          String extraInstruction, String modelName) {
        /** 兼容旧调用：不传模型时走配置默认 */
        public Request(Long sessionId, String formType, String style, String length, String extraInstruction) {
            this(sessionId, formType, style, length, extraInstruction, null);
        }
    }

    /** 分层提示词构建结果，同时把统计信息带给前端 */
    public record PromptBundle(List<Map<String, String>> messages, int messageCount, int dialogueChars) {}

    public PromptBundle build(Long userId, Request req) {
        ChatSession session = sessionMapper.findById(req.sessionId());
        if (session == null || !session.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }

        List<ChatMessage> messages = messageMapper.listBySession(session.getId());
        List<ChatMessage> dialogue = messages.stream()
                .filter(m -> !"system".equals(m.getRole()))
                .filter(m -> StringUtils.hasText(m.getContent()))
                .toList();
        if (dialogue.size() < 4) {
            throw new BusinessException("这场对话太短了，至少需要 4 条消息才能改写成作品");
        }

        CharacterCard card = characterMapper.findById(session.getCharacterId());
        String system = buildSystem(req, card);
        String user = buildUser(session, card, dialogue, req);

        return new PromptBundle(
                List.of(
                        Map.of("role", "system", "content", system),
                        Map.of("role", "user", "content", user)),
                dialogue.size(),
                dialogue.stream().mapToInt(m -> m.getContent().length()).sum());
    }

    /**
     * 第一层：写作规则。显式声明「不要输出对话原文」「不要加解释」，
     * 这是流式直出场景下最容易出问题的地方。
     */
    private String buildSystem(Request req, CharacterCard card) {
        String roleName = card != null && StringUtils.hasText(card.getName()) ? card.getName() : "主角";
        String formHint = switch (orEmpty(req.formType())) {
            case "script" -> """
                    改写为「剧本」：
                    - 用「场景标题」「角色名：台词」的形式呈现，必要的动作描写放在括号里
                    - 保留原对话的冲突与转折，改写的是表达方式而非内容走向
                    - 场景之间空一行，场景标题以「场景一」「场景二」或地点命名
                    """;
            case "outline" -> """
                    改写为「大纲」：
                    - 输出分章节的结构化大纲，不要写成完整正文
                    - 每章包含：章节标题、剧情概要（3～5 句）、出场人物、关键冲突
                    - 只提炼原对话已有的情节，不要新增支线
                    """;
            default -> """
                    改写为「小说」：
                    - 第三人称叙述为主，视角稳定，不要中途切换人称
                    - 有环境描写、动作和心理活动，不要写成对白流水账
                    - 保留原对话中的性格特征与说话习惯，让人物读起来还是同一个人
                    """;
        };
        String styleHint = switch (orEmpty(req.style())) {
            case "colloquial" -> "语言口语化，像讲一件事，不堆砌形容词";
            case "script" -> "戏剧化表达，对白推进，注意节奏与停顿";
            case "poetic" -> "偏诗意，意象密集，适度留白";
            case "literary" -> "文学性叙事，画面感强但克制，不用生僻词";
            default -> "叙事清晰流畅，重在把事情讲明白";
        };
        String lengthHint = switch (orEmpty(req.length())) {
            case "short" -> "篇幅控制在 800 字以内";
            case "long" -> "篇幅可以到 3000 字以上，充分展开";
            case "medium" -> "篇幅 1500 字左右";
            default -> "篇幅与素材量相称";
        };

        return """
                你是一名叙事改写者。你的工作是把「一场角色扮演对话的记录」改写成一篇可独立阅读的作品。

                本次改写的主角是：%s

                %s

                文风要求：%s
                篇幅要求：%s

                硬性规则：
                1. 只输出作品正文，不要输出标题、说明、字数统计或任何解释
                2. 不要出现「用户：」「助手：」这类对话标记
                3. 不要把对话原样复制粘贴，要做叙事化改写
                4. 不要新增对话里没有的核心人物、情节或世界观设定
                5. 涉及亲密或敏感情节时，用含蓄的侧写方式处理，不要写露骨细节
                """.formatted(roleName, formHint, styleHint, lengthHint);
    }

    /**
     * 第二、三、四层：角色设定 + 剧情骨架 + 分层裁剪后的对话原文。
     */
    private String buildUser(ChatSession session, CharacterCard card,
                             List<ChatMessage> dialogue, Request req) {
        StringBuilder sb = new StringBuilder();

        if (card != null) {
            sb.append("【角色设定】\n");
            if (StringUtils.hasText(card.getName())) sb.append("姓名：").append(card.getName()).append('\n');
            if (StringUtils.hasText(card.getPersonality())) sb.append("性格：").append(card.getPersonality()).append('\n');
            if (StringUtils.hasText(card.getBackgroundStory())) sb.append("背景：").append(card.getBackgroundStory()).append('\n');
            if (StringUtils.hasText(card.getDescription())) sb.append("简介：").append(card.getDescription()).append('\n');
            sb.append('\n');
        }

        // 剧情骨架：把长对话按阶段压缩成事件线索，避免原文超预算时剧情也一起丢
        List<ChatMessage> selected = selectByBudget(dialogue);
        if (selected.size() < dialogue.size()) {
            sb.append("【剧情线索】\n");
            for (ChatMessage m : condense(dialogue, 5)) {
                sb.append('-').append(prefixOf(m)).append(trimTo(safe(m.getContent()), 40)).append('\n');
            }
            sb.append('\n');
        }

        sb.append("【对话记录】\n");
        if (selected.size() < dialogue.size()) {
            sb.append("（共 ").append(dialogue.size())
              .append(" 条，以下为等间隔抽取的 ").append(selected.size()).append(" 条）\n");
        }
        for (ChatMessage m : selected) {
            sb.append(prefixOf(m)).append(safe(m.getContent())).append('\n');
        }

        if (StringUtils.hasText(req.extraInstruction())) {
            sb.append("\n【额外要求】\n").append(req.extraInstruction().trim()).append('\n');
        }

        sb.append("\n现在请输出改写后的作品正文。");
        return sb.toString();
    }

    /**
     * 分层裁剪：优先保留首尾（开场与结局），中间等间隔抽样。
     * 首尾承载了建置与收束，丢了这两段改写就没有结构。
     */
    private List<ChatMessage> selectByBudget(List<ChatMessage> dialogue) {
        int totalChars = dialogue.stream().mapToInt(m -> m.getContent() == null ? 0 : m.getContent().length()).sum();
        int maxChars = (int) (DIALOGUE_TOKEN_BUDGET / 0.7);
        if (totalChars <= maxChars) {
            return dialogue;
        }
        int keep = Math.max(8, maxChars / Math.max(1, totalChars / dialogue.size()));
        keep = Math.min(keep, dialogue.size());
        int head = Math.max(2, keep / 4);
        int tail = Math.max(2, keep / 4);
        int middle = keep - head - tail;

        List<ChatMessage> result = new ArrayList<>();
        result.addAll(dialogue.subList(0, head));
        if (middle > 0) {
            int gap = Math.max(1, (dialogue.size() - head - tail) / middle);
            for (int i = 0; i < middle; i++) {
                int idx = head + i * gap;
                if (idx < dialogue.size() - tail) {
                    result.add(dialogue.get(idx));
                }
            }
        }
        result.addAll(dialogue.subList(dialogue.size() - tail, dialogue.size()));
        return result;
    }

    /** 抽取若干条代表性消息做剧情线索 */
    private List<ChatMessage> condense(List<ChatMessage> dialogue, int n) {
        if (dialogue.size() <= n) {
            return dialogue;
        }
        int gap = dialogue.size() / n;
        List<ChatMessage> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(dialogue.get(Math.min(dialogue.size() - 1, i * gap)));
        }
        return out;
    }

    private static String prefixOf(ChatMessage m) {
        return "user".equals(m.getRole()) ? "我：" : "";
    }

    private static String trimTo(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n) + "…";
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }
}
