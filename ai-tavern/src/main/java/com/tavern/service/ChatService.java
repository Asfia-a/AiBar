package com.tavern.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.PageHelper;
import com.tavern.config.BillingProperties;
import com.tavern.common.BusinessException;
import com.tavern.common.ErrorCode;
import com.tavern.common.PageResult;
import com.tavern.entity.*;
import com.tavern.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final CharacterMapper characterMapper;
    private final TagMapper tagMapper;
    private final UserMapper userMapper;
    private final TokenLogMapper tokenLogMapper;
    private final MessageCollectMapper messageCollectMapper;
    private final AiModelService aiModelService;
    private final ContentAuditService contentAuditService;
    private final BillingProperties billingProperties;
    private final ObjectMapper objectMapper;
    private final RuntimeMetricsService runtimeMetricsService;

    @Value("${ai.model.context-window-size:20}")
    private int defaultContextWindow;

    private final ExecutorService sseExecutor = Executors.newCachedThreadPool();

    public ChatSession createSession(Long userId, Long characterId, String title) {
        CharacterCard card = characterMapper.findById(characterId);
        if (card == null || card.getStatus() != 2) {
            throw new BusinessException(ErrorCode.CHARACTER_NOT_FOUND);
        }
        // 同一用户同一角色只保留一个会话，避免重复历史
        ChatSession existing = sessionMapper.findLatestByUserAndCharacter(userId, characterId);
        if (existing != null) {
            ensurePromptSnapshot(existing, card);
            return sessionMapper.findById(existing.getId());
        }
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setCharacterId(characterId);
        session.setTitle(title != null ? title : card.getName());
        session.setMessageCount(0);
        session.setTotalTokens(0L);
        session.setPromptSnapshot(buildPromptSnapshot(card));
        sessionMapper.insert(session);

        if (card.getGreeting() != null && !card.getGreeting().isBlank()) {
            ChatMessage greet = new ChatMessage();
            greet.setSessionId(session.getId());
            greet.setRole("assistant");
            greet.setContent(card.getGreeting());
            greet.setTokens(0);
            messageMapper.insert(greet);
            session.setLastMessage(card.getGreeting());
            session.setLastRole("assistant");
            session.setMessageCount(1);
            sessionMapper.update(session);
        }
        return sessionMapper.findById(session.getId());
    }

    public List<ChatSession> listSessions(Long userId) {
        return sessionMapper.listByUser(userId);
    }

    public void deleteSession(Long userId, Long id) {
        sessionMapper.delete(id, userId);
    }

    public List<ChatMessage> listMessages(Long userId, Long sessionId) {
        ChatSession session = sessionMapper.findById(sessionId);
        if (session == null || !session.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }
        return messageMapper.listBySession(sessionId);
    }

    public SseEmitter stream(Long userId, Long sessionId, Long characterId, String message) {
        SseEmitter emitter = new SseEmitter(180_000L);
        emitter.onTimeout(emitter::complete);
        emitter.onError(e -> log.warn("SSE error callback: {}", e.toString()));
        sseExecutor.execute(() -> {
            try {
                doStream(userId, sessionId, characterId, message, emitter, null);
            } catch (Exception e) {
                log.error("SSE error", e);
                try {
                    emitter.send(SseEmitter.event().name("error").data(e.getMessage() == null ? "error" : e.getMessage(), MediaType.TEXT_PLAIN));
                } catch (IOException ignored) {
                }
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    public SseEmitter regenerate(Long userId, Long messageId) {
        ChatMessage old = messageMapper.findById(messageId);
        if (old == null || !"assistant".equals(old.getRole())) {
            throw new BusinessException("只能重新生成助手消息");
        }
        ChatSession session = sessionMapper.findById(old.getSessionId());
        if (session == null || !session.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }
        List<ChatMessage> all = messageMapper.listBySession(session.getId());
        String userMsg = null;
        for (int i = all.size() - 1; i >= 0; i--) {
            if (all.get(i).getId().equals(messageId)) {
                for (int j = i - 1; j >= 0; j--) {
                    if ("user".equals(all.get(j).getRole())) {
                        userMsg = all.get(j).getContent();
                        break;
                    }
                }
                break;
            }
        }
        if (userMsg == null) {
            throw new BusinessException("未找到对应用户消息");
        }
        messageMapper.deleteById(messageId);
        SseEmitter emitter = new SseEmitter(180_000L);
        String finalUserMsg = userMsg;
        sseExecutor.execute(() -> {
            try {
                doStream(userId, session.getId(), session.getCharacterId(), finalUserMsg, emitter, true);
            } catch (Exception e) {
                log.error("regenerate error", e);
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    private void doStream(Long userId, Long sessionId, Long characterId, String message,
                          SseEmitter emitter, Boolean skipUserInsert) throws IOException {
        User user = userMapper.findById(userId);
        if (user.getPoints() == null || user.getPoints() <= 0) {
            emitter.send(SseEmitter.event().name("error")
                    .data(ErrorCode.TOKEN_INSUFFICIENT.getMessage(), MediaType.TEXT_PLAIN));
            emitter.complete();
            return;
        }

        ChatSession session;
        if (sessionId == null) {
            session = createSession(userId, characterId, null);
        } else {
            session = sessionMapper.findById(sessionId);
            if (session == null || !session.getUserId().equals(userId)) {
                throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
            }
        }

        CharacterCard liveCard = characterMapper.findById(session.getCharacterId());
        if (liveCard == null) {
            throw new BusinessException(ErrorCode.CHARACTER_NOT_FOUND);
        }
        // 冻结人设：创作者后续改卡不影响本会话
        ensurePromptSnapshot(session, liveCard);
        session = sessionMapper.findById(session.getId());
        CharacterCard promptCard = resolvePromptCard(session, liveCard);

        if (skipUserInsert == null || !skipUserInsert) {
            // 用户输入发送前同步过审，命中直接拒，不写库也不发出去
            List<String> hit = contentAuditService.auditSync(message);
            if (!hit.isEmpty()) {
                contentAuditService.recordSyncHit(userId, session.getId(), null, message, hit);
                emitter.send(SseEmitter.event().name("error")
                        .data("内容包含违规信息：" + String.join("、", hit), MediaType.TEXT_PLAIN));
                emitter.complete();
                return;
            }
            ChatMessage userMsg = new ChatMessage();
            userMsg.setSessionId(session.getId());
            userMsg.setRole("user");
            userMsg.setContent(message);
            userMsg.setTokens(0);
            messageMapper.insert(userMsg);
            contentAuditService.auditAsync(userId, session.getId(), userMsg.getId(), message);
        }

        PromptBundle bundle = buildPrompt(promptCard, session.getId());
        List<Map<String, String>> prompt = bundle.messages();
        double temperature = promptCard.getTemperature() == null ? 0.7 : promptCard.getTemperature().doubleValue();
        double topP = promptCard.getTopP() == null ? 0.9 : promptCard.getTopP().doubleValue();
        int maxTokens = promptCard.getMaxTokens() == null ? 500 : promptCard.getMaxTokens();

        // 上下文裁剪统计既回给前端（画占用条），也记进运行指标（画趋势）
        runtimeMetricsService.recordContext(bundle);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("sessionId", session.getId());
        meta.put("window", bundle.window());
        meta.put("usedRounds", bundle.usedRounds());
        meta.put("droppedRounds", bundle.droppedRounds());
        meta.put("systemChars", bundle.systemChars());
        meta.put("dialogueChars", bundle.dialogueChars());
        meta.put("estimatedTokens", bundle.estimatedTokens());
        meta.put("maxTokens", maxTokens);

        emitter.send(SseEmitter.event()
                .name("meta")
                .data(objectMapper.writeValueAsString(meta), MediaType.TEXT_PLAIN));

        AiModelService.StreamResult result = aiModelService.streamChat(prompt, temperature, topP, maxTokens, delta -> {
            try {
                if (delta != null && !delta.isEmpty()) {
                    emitter.send(SseEmitter.event().data(delta, MediaType.TEXT_PLAIN));
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        result = sanitizeOutput(userId, session, result);
        finishChat(userId, session, liveCard, result);
        emitter.send(SseEmitter.event().data("[DONE]", MediaType.TEXT_PLAIN));
        emitter.complete();
    }

    /**
     * 模型输出审核。
     * <p>
     * 与用户输入不同，模型输出已经流式推给用户了，事后拦不住；
     * 因此这里的处理是「落库前截断 + 记违规日志」：
     * 数据库与后续展示里存的是截断后的干净版本，违规痕迹留在 {@code t_violation_log} 里可追溯。
     * 这个取舍在论文里要如实写明，不能说成是「实时拦截」。
     */
    private AiModelService.StreamResult sanitizeOutput(Long userId, ChatSession session, AiModelService.StreamResult result) {
        String content = result.content();
        if (content == null || content.isBlank()) {
            return result;
        }
        List<String> hit = contentAuditService.auditSync(content);
        if (hit.isEmpty()) {
            return result;
        }
        String cut = truncateAround(content, hit.get(0));
        String replaced = cut + "\n\n（该段内容因包含不当信息已被系统隐去）";
        contentAuditService.recordSyncHit(userId, session.getId(), null, content, hit);
        return new AiModelService.StreamResult(replaced, result.totalTokens());
    }

    /**
     * 在命中处截断，保留命中前最近一个句读作为自然断点。
     * 找不到合适断点时退回固定长度截断。
     */
    private String truncateAround(String content, String word) {
        int maxKeep = 120;
        if (content.length() <= maxKeep) {
            return content;
        }
        int idx = indexOfIgnoreCase(content, word);
        if (idx < 0) {
            return content.substring(0, maxKeep);
        }
        int cut = Math.min(idx, content.length());
        // 从命中处往前找一个句号/问号/叹号/换行，让截断落在句子边界
        for (int i = idx - 1; i > Math.max(0, idx - 40); i--) {
            char c = content.charAt(i);
            if (c == '。' || c == '？' || c == '！' || c == '?' || c == '!' || c == '\n') {
                cut = i + 1;
                break;
            }
        }
        return content.substring(0, Math.max(1, cut));
    }

    private int indexOfIgnoreCase(String haystack, String needle) {
        if (needle == null || needle.isEmpty()) {
            return -1;
        }
        return haystack.toLowerCase().indexOf(needle.toLowerCase());
    }

    @Transactional
    protected void finishChat(Long userId, ChatSession session, CharacterCard card, AiModelService.StreamResult result) {
        int apiTokens = result.totalTokens();
        int pointsCost = billingProperties.toPoints(apiTokens);
        User user = userMapper.findById(userId);
        long before = user.getPoints() == null ? 0L : user.getPoints();
        int deducted = userMapper.deductPoints(userId, pointsCost);
        if (deducted == 0) {
            pointsCost = before > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) before;
            if (pointsCost > 0) {
                userMapper.deductPoints(userId, pointsCost);
            }
        }
        long after = Math.max(0, before - pointsCost);

        ChatMessage aiMsg = new ChatMessage();
        aiMsg.setSessionId(session.getId());
        aiMsg.setRole("assistant");
        aiMsg.setContent(result.content());
        aiMsg.setTokens(apiTokens);
        messageMapper.insert(aiMsg);

        session.setLastMessage(result.content());
        session.setLastRole("assistant");
        session.setMessageCount((session.getMessageCount() == null ? 0 : session.getMessageCount()) + (deducted >= 0 ? 2 : 1));
        session.setTotalTokens((session.getTotalTokens() == null ? 0 : session.getTotalTokens()) + apiTokens);
        sessionMapper.update(session);

        TokenLog tokenLog = new TokenLog();
        tokenLog.setUserId(userId);
        tokenLog.setSessionId(session.getId());
        tokenLog.setTokensUsed(pointsCost);
        tokenLog.setBalanceBefore(before);
        tokenLog.setBalanceAfter(after);
        tokenLog.setType(1);
        tokenLogMapper.insert(tokenLog);

        characterMapper.incrChatCount(card.getId());
        contentAuditService.auditAsync(userId, session.getId(), aiMsg.getId(), result.content());
    }

    /** 为缺少快照的已有会话补冻当前人设（启动时调用一次） */
    public int backfillMissingSnapshots() {
        List<ChatSession> missing = sessionMapper.listMissingSnapshots();
        int n = 0;
        for (ChatSession s : missing) {
            CharacterCard card = characterMapper.findById(s.getCharacterId());
            if (card == null) continue;
            String snap = buildPromptSnapshot(card);
            if (sessionMapper.updatePromptSnapshot(s.getId(), snap) > 0) {
                n++;
            }
        }
        return n;
    }

    private void ensurePromptSnapshot(ChatSession session, CharacterCard card) {
        if (session.getPromptSnapshot() != null && !session.getPromptSnapshot().isBlank()) {
            return;
        }
        sessionMapper.updatePromptSnapshot(session.getId(), buildPromptSnapshot(card));
    }

    private String buildPromptSnapshot(CharacterCard card) {
        try {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("characterId", card.getId());
            m.put("version", card.getVersion() == null ? 1 : card.getVersion());
            m.put("name", card.getName());
            m.put("description", card.getDescription());
            m.put("personality", card.getPersonality());
            m.put("backgroundStory", card.getBackgroundStory());
            m.put("exampleDialogue", card.getExampleDialogue());
            m.put("temperature", card.getTemperature());
            m.put("topP", card.getTopP());
            m.put("maxTokens", card.getMaxTokens());
            List<Tag> tags = tagMapper.findByCharacterId(card.getId());
            m.put("tagNames", tags.stream().map(Tag::getName).collect(Collectors.toList()));
            return objectMapper.writeValueAsString(m);
        } catch (Exception e) {
            throw new BusinessException("冻结角色人设失败");
        }
    }

    private CharacterCard resolvePromptCard(ChatSession session, CharacterCard live) {
        String json = session.getPromptSnapshot();
        if (json == null || json.isBlank()) {
            return live;
        }
        try {
            Map<String, Object> m = objectMapper.readValue(json, new TypeReference<>() {});
            CharacterCard card = new CharacterCard();
            card.setId(live.getId());
            card.setName(asString(m.get("name"), live.getName()));
            card.setDescription(asString(m.get("description"), live.getDescription()));
            card.setPersonality(asString(m.get("personality"), live.getPersonality()));
            card.setBackgroundStory(asString(m.get("backgroundStory"), live.getBackgroundStory()));
            card.setExampleDialogue(asString(m.get("exampleDialogue"), live.getExampleDialogue()));
            card.setTemperature(asDecimal(m.get("temperature"), live.getTemperature()));
            card.setTopP(asDecimal(m.get("topP"), live.getTopP()));
            card.setMaxTokens(asInt(m.get("maxTokens"), live.getMaxTokens()));
            Object tags = m.get("tagNames");
            if (tags instanceof List<?> list) {
                List<Tag> tagEntities = new ArrayList<>();
                for (Object t : list) {
                    Tag tag = new Tag();
                    tag.setName(String.valueOf(t));
                    tagEntities.add(tag);
                }
                card.setTags(tagEntities);
            }
            return card;
        } catch (Exception e) {
            log.warn("解析会话人设快照失败 sessionId={}, 回退实时角色卡", session.getId());
            return live;
        }
    }

    /**
     * 构建提示词，同时把裁剪过程统计出来。
     *
     * <p>{@code PromptBundle} 里的数字是「上下文占用条」的数据来源：
     * 窗口上限、实际取了几轮、system 提示多长、被丢弃了多少轮。
     * 这些之前只体现在代码里，用户和论文都看不到，现在显式返回出来。
     */
    private PromptBundle buildPrompt(CharacterCard card, Long sessionId) {
        String style = "";
        if (card.getTags() != null && !card.getTags().isEmpty()) {
            style = card.getTags().stream().map(Tag::getName).filter(Objects::nonNull).collect(Collectors.joining("、"));
        } else if (card.getId() != null) {
            style = tagMapper.findByCharacterId(card.getId()).stream().map(Tag::getName).collect(Collectors.joining("、"));
        }
        StringBuilder system = new StringBuilder();
        system.append("你是一个角色扮演 AI，请始终保持角色身份，不要跳出人设。\n");
        if (card.getPersonality() != null) {
            system.append("【性格】").append(card.getPersonality()).append("\n");
        }
        if (card.getDescription() != null && !card.getDescription().isBlank()
                && (card.getPersonality() == null || !card.getPersonality().contains(card.getDescription()))) {
            system.append("【简介】").append(card.getDescription()).append("\n");
        }
        if (card.getBackgroundStory() != null) {
            system.append("【背景】").append(card.getBackgroundStory()).append("\n");
        }
        if (card.getExampleDialogue() != null) {
            system.append("【示例对话】").append(card.getExampleDialogue()).append("\n");
        }
        if (!style.isBlank()) {
            system.append("【说话风格标签】").append(style).append("。请据此调整语气与用词。\n");
        }

        int window = defaultContextWindow;
        ModelConfig cfg = aiModelService.resolveConfig();
        if (cfg.getContextWindowSize() != null && cfg.getContextWindowSize() > 0) {
            window = cfg.getContextWindowSize();
        }

        List<ChatMessage> recent = messageMapper.listRecent(sessionId, window);
        Collections.reverse(recent);

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", system.toString()));
        int systemChars = system.length();
        int dialogueChars = 0;
        int usedRounds = 0;
        for (ChatMessage m : recent) {
            if ("system".equals(m.getRole())) continue;
            messages.add(Map.of("role", m.getRole(), "content", m.getContent()));
            dialogueChars += m.getContent() == null ? 0 : m.getContent().length();
            usedRounds++;
        }
        // 会话总消息数，用于算出被窗口截断掉多少
        int totalMessages = messageMapper.countBySession(sessionId);
        int dropped = Math.max(totalMessages - usedRounds, 0);
        return new PromptBundle(messages, window, usedRounds, dropped, systemChars, dialogueChars);
    }

    /**
     * 提示词与其构建统计。
     *
     * @param window       配置的上下文窗口（轮）
     * @param usedRounds   实际送进模型的对话轮数
     * @param droppedRounds 因超出窗口被丢弃的轮数
     * @param systemChars  system 提示长度（字符）
     * @param dialogueChars 送入的历史对话总长度（字符）
     */
    public record PromptBundle(List<Map<String, String>> messages, int window, int usedRounds,
                               int droppedRounds, int systemChars, int dialogueChars) {
        /** 粗略估算 token 数：中文按 1.5 字/token 折算 */
        public int estimatedTokens() {
            return (int) ((systemChars + dialogueChars) / 1.5);
        }
    }

    public void collectMessage(Long userId, Long messageId) {
        messageCollectMapper.insert(userId, messageId);
    }

    public PageResult<ChatMessage> search(Long userId, String keyword, int page, int size) {
        PageHelper.startPage(page, size);
        return PageResult.of(messageMapper.search(userId, keyword));
    }

    private static String asString(Object v, String fallback) {
        return v == null ? fallback : String.valueOf(v);
    }

    private static BigDecimal asDecimal(Object v, BigDecimal fallback) {
        if (v == null) return fallback;
        try {
            return new BigDecimal(String.valueOf(v));
        } catch (Exception e) {
            return fallback;
        }
    }

    private static Integer asInt(Object v, Integer fallback) {
        if (v == null) return fallback;
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception e) {
            return fallback;
        }
    }
}
