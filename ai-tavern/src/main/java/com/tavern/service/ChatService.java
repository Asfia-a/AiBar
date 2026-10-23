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
import com.tavern.prompt.PromptBundle;
import com.tavern.prompt.PromptEngine;
import com.tavern.prompt.PromptInput;
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
    private final MessageSwipeMapper messageSwipeMapper;
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
    private final PromptEngine promptEngine;
    private final WorldInfoMapper worldInfoMapper;

    @Value("${ai.model.context-window-size:20}")
    private int defaultContextWindow;

    /** 输入侧 token 预算，超出先裁最早的历史 */
    @Value("${ai.model.max-input-tokens:8000}")
    private int maxInputTokens;

    /** 世界书扫描最近几条消息 */
    @Value("${ai.model.world-info-scan-depth:4}")
    private int worldInfoScanDepth;

    /** 作者注释是强指令，限个长度免得把上下文窗口挤爆 */
    private static final int MAX_AUTHOR_NOTE_CHARS = 2000;

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

    /**
     * 读会话级的「作者注释」设置。
     *
     * <p>作者注释不是角色卡的一部分：同一张卡，不同会话可以有不同的一句强指令
     * （比如这一段想让它写得更阴郁），所以挂在 t_chat_session 上。
     */
    public Map<String, Object> authorNote(Long userId, Long sessionId) {
        return toAuthorNoteMap(requireOwnSession(userId, sessionId));
    }

    /** 保存作者注释；文本清空等于关掉这条指令 */
    public Map<String, Object> saveAuthorNote(Long userId, Long sessionId, String note,
                                              Integer depth, Integer frequency) {
        requireOwnSession(userId, sessionId);
        String text = note == null ? "" : note.trim();
        if (text.length() > MAX_AUTHOR_NOTE_CHARS) {
            throw new BusinessException("作者注释最多 " + MAX_AUTHOR_NOTE_CHARS + " 字");
        }
        // depth=0 表示插在历史末尾；frequency=0 表示不插
        int d = clamp(depth, 0, 50, 0);
        int f = clamp(frequency, 0, 100, 1);
        sessionMapper.updateAuthorNote(sessionId, userId, text.isEmpty() ? null : text, d, f);
        return toAuthorNoteMap(sessionMapper.findById(sessionId));
    }

    private ChatSession requireOwnSession(Long userId, Long sessionId) {
        ChatSession session = sessionMapper.findById(sessionId);
        if (session == null || userId == null || !userId.equals(session.getUserId())) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }
        return session;
    }

    private Map<String, Object> toAuthorNoteMap(ChatSession s) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sessionId", s.getId());
        out.put("authorNote", s.getAuthorNote());
        out.put("depth", s.getAnDepth() == null ? 0 : s.getAnDepth());
        out.put("frequency", s.getAnFrequency() == null ? 1 : s.getAnFrequency());
        out.put("active", s.getAuthorNote() != null && !s.getAuthorNote().isBlank()
                && (s.getAnFrequency() == null || s.getAnFrequency() > 0));
        return out;
    }

    private static int clamp(Integer value, int min, int max, int fallback) {
        if (value == null) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }

    public SseEmitter stream(Long userId, Long sessionId, Long characterId, String message) {
        SseEmitter emitter = new SseEmitter(180_000L);
        emitter.onTimeout(emitter::complete);
        emitter.onError(e -> log.warn("SSE error callback: {}", e.toString()));
        sseExecutor.execute(() -> {
            try {
                doStream(userId, sessionId, characterId, message, emitter, null, null);
            } catch (Exception e) {
                failStream(emitter, e, "SSE error");
            }
        });
        return emitter;
    }

    /**
     * SSE 失败收尾。
     *
     * <p>这里不能调 {@code completeWithError}：响应已经是 text/event-stream 且已提交，
     * Spring 会再派发一次 /error，试图把 Result 序列化成 SSE，
     * 结果是客户端只看到「连接已断开」（前端拿不到真正的错误原因），日志里还多一坨
     * {@code HttpMessageNotWritableException}。正确做法是把错误当成一条 error 事件写出去再正常收尾。
     */
    private void failStream(SseEmitter emitter, Exception e, String logTag) {
        log.error(logTag, e);
        try {
            emitter.send(SseEmitter.event().name("error")
                    .data(e.getMessage() == null ? "流式输出失败" : e.getMessage(), MediaType.TEXT_PLAIN));
        } catch (Exception ignored) {
            // 客户端已经断开，写不进去就算了
        }
        try {
            emitter.complete();
        } catch (Exception ignored) {
            // 已经 complete 过，忽略
        }
    }

    /**
     * 重新生成：不再删旧消息，而是给同一条消息追加一个新版本（swipe）。
     *
     * <p>旧实现是「删掉再生成」，一旦模型这次答得更差就找不回上一条；
     * 改成多版本后，重新生成 = 向右滑一格，历史版本都留着。
     */
    public SseEmitter regenerate(Long userId, Long messageId) {
        return swipe(userId, messageId);
    }

    /** 给某条助手消息再生成一个版本，流式返回；老版本留在 t_message_swipe */
    public SseEmitter swipe(Long userId, Long messageId) {
        ChatMessage target = requireOwnAssistantMessage(userId, messageId);
        SseEmitter emitter = new SseEmitter(180_000L);
        emitter.onTimeout(emitter::complete);
        emitter.onError(e -> log.warn("SSE swipe error callback: {}", e.toString()));
        sseExecutor.execute(() -> {
            try {
                doStream(userId, target.getSessionId(), null, null, emitter, true, target.getId());
            } catch (Exception e) {
                failStream(emitter, e, "swipe error");
            }
        });
        return emitter;
    }

    /** 左右切版本：纯读库，不调模型，不扣积分 */
    @Transactional
    public Map<String, Object> selectSwipe(Long userId, Long messageId, int idx) {
        ChatMessage msg = requireOwnAssistantMessage(userId, messageId);
        MessageSwipe swipe = messageSwipeMapper.findByMessageAndIdx(messageId, idx);
        if (swipe == null) {
            throw new BusinessException("没有第 " + (idx + 1) + " 个版本");
        }
        int count = Math.max(msg.getSwipeCount() == null ? 1 : msg.getSwipeCount(), idx + 1);
        int tokens = swipe.getTokens() == null ? 0 : swipe.getTokens();
        messageMapper.updateActive(messageId, swipe.getContent(), tokens, idx, count);
        syncSessionTail(msg.getSessionId(), messageId, swipe.getContent(), "assistant");

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("messageId", messageId);
        out.put("content", swipe.getContent());
        out.put("swipeIndex", idx);
        out.put("swipeCount", count);
        return out;
    }

    /**
     * 编辑消息。
     *
     * <p>用户消息与助手消息都允许改：改错别字、删掉一句带偏剧情的描述都很常见。
     * 编辑不过模型、不动计费，但要走一遍审核——否则这里就成了绕过敏感词的后门。
     */
    @Transactional
    public Map<String, Object> editMessage(Long userId, Long messageId, String content) {
        ChatMessage msg = requireOwnMessage(userId, messageId);
        String text = content == null ? "" : content.trim();
        if (text.isEmpty()) {
            throw new BusinessException("内容不能为空");
        }
        List<String> hits = contentAuditService.auditSync(text);
        if (!hits.isEmpty()) {
            contentAuditService.recordSyncHit(userId, msg.getSessionId(), msg.getId(), text, hits);
            throw new BusinessException("内容包含违规信息：" + String.join("、", hits));
        }
        messageMapper.updateContent(messageId, text);

        int idx = msg.getSwipeIndex() == null ? 0 : msg.getSwipeIndex();
        if (msg.getSwipeCount() != null && msg.getSwipeCount() > 1) {
            messageSwipeMapper.updateContent(messageId, idx, text);
        }
        syncSessionTail(msg.getSessionId(), messageId, text, msg.getRole());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("messageId", messageId);
        out.put("content", text);
        return out;
    }

    /**
     * 分支：以某条消息为终点，复制出一段新会话。
     *
     * <p>作用是「保留当前走向，再试另一条剧情线」——原会话一个字都不动。
     * 人设快照、作者注释一并带过去，否则分支出来的语感会变。
     */
    @Transactional
    public ChatSession branch(Long userId, Long messageId) {
        ChatMessage msg = requireOwnMessage(userId, messageId);
        ChatSession src = sessionMapper.findById(msg.getSessionId());
        CharacterCard card = characterMapper.findById(src.getCharacterId());
        if (card == null) {
            throw new BusinessException(ErrorCode.CHARACTER_NOT_FOUND);
        }

        ChatSession copy = new ChatSession();
        copy.setUserId(userId);
        copy.setCharacterId(src.getCharacterId());
        copy.setTitle((src.getTitle() == null || src.getTitle().isBlank() ? card.getName() : src.getTitle()) + " · 分支");
        copy.setMessageCount(0);
        copy.setTotalTokens(0L);
        copy.setPromptSnapshot(src.getPromptSnapshot());
        sessionMapper.insert(copy);

        if (src.getAuthorNote() != null) {
            sessionMapper.updateAuthorNote(copy.getId(), userId, src.getAuthorNote(),
                    src.getAnDepth() == null ? 0 : src.getAnDepth(),
                    src.getAnFrequency() == null ? 1 : src.getAnFrequency());
        }

        List<ChatMessage> all = messageMapper.listBySession(src.getId());
        int copied = 0;
        String lastContent = null;
        String lastRole = null;
        for (ChatMessage m : all) {
            if (m.getId() > messageId) {
                break;
            }
            ChatMessage item = new ChatMessage();
            item.setSessionId(copy.getId());
            item.setRole(m.getRole());
            item.setContent(m.getContent());
            item.setTokens(0);
            messageMapper.insert(item);
            copied++;
            lastContent = m.getContent();
            lastRole = m.getRole();
        }

        ChatSession created = sessionMapper.findById(copy.getId());
        created.setMessageCount(copied);
        created.setLastMessage(lastContent);
        created.setLastRole(lastRole);
        sessionMapper.update(created);
        return created;
    }

    /**
     * 删除会话里选中的消息（硬删除）。
     *
     * <p>删掉之后 {@code buildPrompt} 就读不到它们了，等于从模型记忆里清掉；
     * 已经扣掉的积分不退回，{@code total_tokens} 继续当「累计消耗」用。
     */
    @Transactional
    public Map<String, Object> deleteMessages(Long userId, Long sessionId, List<Long> ids) {
        ChatSession session = requireOwnSession(userId, sessionId);
        List<Long> clean = ids == null ? List.of() : ids.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (clean.isEmpty()) {
            throw new BusinessException("没有选中要删除的消息");
        }

        // 带上 session_id 条件，别人的消息即使传了 id 也删不掉
        int deleted = messageMapper.deleteByIds(sessionId, clean);
        if (deleted == 0) {
            throw new BusinessException("这些消息不属于当前会话");
        }

        // 消息没了，它的历史版本和收藏记录不能留成孤儿
        messageSwipeMapper.deleteByMessageIds(clean);
        messageCollectMapper.deleteByMessageIds(clean);

        // 重算会话摘要：条数、最后一条、最后说话的角色
        ChatMessage last = messageMapper.findLast(sessionId);
        int remaining = messageMapper.countBySession(sessionId);
        session.setMessageCount(remaining);
        session.setLastMessage(last == null ? null : last.getContent());
        session.setLastRole(last == null ? null : last.getRole());
        sessionMapper.update(session);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("deleted", deleted);
        out.put("messageCount", remaining);
        out.put("lastMessage", session.getLastMessage());
        return out;
    }

    private ChatMessage requireOwnMessage(Long userId, Long messageId) {
        ChatMessage msg = messageMapper.findById(messageId);
        if (msg == null) {
            throw new BusinessException("消息不存在");
        }
        ChatSession session = sessionMapper.findById(msg.getSessionId());
        if (session == null || userId == null || !userId.equals(session.getUserId())) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }
        return msg;
    }

    private ChatMessage requireOwnAssistantMessage(Long userId, Long messageId) {
        ChatMessage msg = requireOwnMessage(userId, messageId);
        if (!"assistant".equals(msg.getRole())) {
            throw new BusinessException("只能对助手回复做这个操作");
        }
        return msg;
    }

    /** 改的正好是最后一条时，会话列表上的摘要也要跟着变 */
    private void syncSessionTail(Long sessionId, Long messageId, String content, String role) {
        if (messageMapper.countAfter(sessionId, messageId) > 0) {
            return;
        }
        ChatSession session = sessionMapper.findById(sessionId);
        if (session == null) {
            return;
        }
        session.setLastMessage(content);
        session.setLastRole(role);
        sessionMapper.update(session);
    }

    private void doStream(Long userId, Long sessionId, Long characterId, String message,
                          SseEmitter emitter, Boolean skipUserInsert, Long swipeMessageId) throws IOException {
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

        // 滑动重生：老消息要留在库里，所以改用「以这条消息为界」的方式裁历史
        ChatMessage swipeTarget = null;
        if (swipeMessageId != null) {
            swipeTarget = messageMapper.findById(swipeMessageId);
            if (swipeTarget == null || !"assistant".equals(swipeTarget.getRole())
                    || !session.getId().equals(swipeTarget.getSessionId())) {
                throw new BusinessException("找不到要重写的回复");
            }
        }

        PromptBundle bundle = buildPrompt(promptCard, session.getId(),
                swipeTarget == null ? null : swipeTarget.getId());
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
        meta.put("worldInfoChars", bundle.worldInfoChars());
        meta.put("authorNoteChars", bundle.authorNoteChars());
        meta.put("activatedEntries", bundle.activatedEntries());
        meta.put("estimatedTokens", bundle.estimatedTokens());
        meta.put("maxTokens", maxTokens);

        emitter.send(SseEmitter.event()
                .name("meta")
                .data(objectMapper.writeValueAsString(meta), MediaType.TEXT_PLAIN));

        AiModelService.ChatParams params = new AiModelService.ChatParams(
                temperature, topP, maxTokens,
                promptCard.getTopK(),
                toDouble(promptCard.getFrequencyPenalty()),
                toDouble(promptCard.getPresencePenalty()),
                toDouble(promptCard.getRepetitionPenalty()),
                AiModelService.parseStopWords(promptCard.getStopWords()));

        AiModelService.StreamResult result = aiModelService.streamChat(prompt, params, delta -> {
            try {
                if (delta != null && !delta.isEmpty()) {
                    emitter.send(SseEmitter.event().data(delta, MediaType.TEXT_PLAIN));
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        result = sanitizeOutput(userId, session, result);
        if (swipeTarget != null) {
            finishSwipe(userId, session, liveCard, result, swipeTarget);
        } else {
            finishChat(userId, session, liveCard, result);
        }
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
        Charge charge = charge(userId, apiTokens);

        ChatMessage aiMsg = new ChatMessage();
        aiMsg.setSessionId(session.getId());
        aiMsg.setRole("assistant");
        aiMsg.setContent(result.content());
        aiMsg.setTokens(apiTokens);
        messageMapper.insert(aiMsg);

        session.setLastMessage(result.content());
        session.setLastRole("assistant");
        session.setMessageCount((session.getMessageCount() == null ? 0 : session.getMessageCount()) + 2);
        session.setTotalTokens((session.getTotalTokens() == null ? 0 : session.getTotalTokens()) + apiTokens);
        sessionMapper.update(session);

        logToken(userId, session.getId(), charge);
        characterMapper.incrChatCount(card.getId());
        contentAuditService.auditAsync(userId, session.getId(), aiMsg.getId(), result.content());
    }

    /**
     * 滑动重生落库：新版本追加进 {@code t_message_swipe}，并设为当前展示。
     *
     * <p>第一次滑动时，先把原内容存成 idx=0，否则「第一版」会丢——
     * 用户滑回来就找不到最初那条了。
     */
    @Transactional
    protected void finishSwipe(Long userId, ChatSession session, CharacterCard card,
                               AiModelService.StreamResult result, ChatMessage target) {
        int apiTokens = result.totalTokens();
        Charge charge = charge(userId, apiTokens);

        List<MessageSwipe> versions = messageSwipeMapper.findByMessage(target.getId());
        if (versions.isEmpty()) {
            MessageSwipe first = new MessageSwipe();
            first.setMessageId(target.getId());
            first.setIdx(0);
            first.setContent(target.getContent());
            first.setTokens(target.getTokens() == null ? 0 : target.getTokens());
            messageSwipeMapper.insert(first);
            versions = messageSwipeMapper.findByMessage(target.getId());
        }
        int nextIdx = versions.stream().mapToInt(v -> v.getIdx() == null ? 0 : v.getIdx()).max().orElse(0) + 1;

        MessageSwipe swipe = new MessageSwipe();
        swipe.setMessageId(target.getId());
        swipe.setIdx(nextIdx);
        swipe.setContent(result.content());
        swipe.setTokens(apiTokens);
        messageSwipeMapper.insert(swipe);

        messageMapper.updateActive(target.getId(), result.content(), apiTokens, nextIdx, nextIdx + 1);

        // 只有重写的是最后一轮，才动会话摘要；否则会话列表会显示得莫名其妙
        if (messageMapper.countAfter(session.getId(), target.getId()) == 0) {
            session.setLastMessage(result.content());
            session.setLastRole("assistant");
        }
        session.setTotalTokens((session.getTotalTokens() == null ? 0 : session.getTotalTokens()) + apiTokens);
        sessionMapper.update(session);

        logToken(userId, session.getId(), charge);
        characterMapper.incrChatCount(card.getId());
        contentAuditService.auditAsync(userId, session.getId(), target.getId(), result.content());
    }

    private record Charge(int points, long before, long after) {}

    /** 扣积分；余额不足时按剩余额度扣，不抛异常（沿用原来的容错口径） */
    private Charge charge(Long userId, int apiTokens) {
        int points = billingProperties.toPoints(apiTokens);
        User user = userMapper.findById(userId);
        long before = user.getPoints() == null ? 0L : user.getPoints();
        int deducted = userMapper.deductPoints(userId, points);
        if (deducted == 0) {
            points = before > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) before;
            if (points > 0) {
                userMapper.deductPoints(userId, points);
            }
        }
        return new Charge(points, before, Math.max(0, before - points));
    }

    private void logToken(Long userId, Long sessionId, Charge charge) {
        TokenLog tokenLog = new TokenLog();
        tokenLog.setUserId(userId);
        tokenLog.setSessionId(sessionId);
        tokenLog.setTokensUsed(charge.points());
        tokenLog.setBalanceBefore(charge.before());
        tokenLog.setBalanceAfter(charge.after());
        tokenLog.setType(1);
        tokenLogMapper.insert(tokenLog);
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
            // 采样参数也一起冻结：老会话不该因为作者事后调参而变成另一种文风
            m.put("topK", card.getTopK());
            m.put("frequencyPenalty", card.getFrequencyPenalty());
            m.put("presencePenalty", card.getPresencePenalty());
            m.put("repetitionPenalty", card.getRepetitionPenalty());
            m.put("stopWords", card.getStopWords());
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
            // 老快照里没有这几个字段，回落到实时卡；新会话则用冻结值
            card.setTopK(asInt(m.get("topK"), live.getTopK()));
            card.setFrequencyPenalty(asDecimal(m.get("frequencyPenalty"), live.getFrequencyPenalty()));
            card.setPresencePenalty(asDecimal(m.get("presencePenalty"), live.getPresencePenalty()));
            card.setRepetitionPenalty(asDecimal(m.get("repetitionPenalty"), live.getRepetitionPenalty()));
            card.setStopWords(m.get("stopWords") == null ? live.getStopWords() : String.valueOf(m.get("stopWords")));
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
    /**
     * 组装本轮发给模型的 messages。
     *
     * <p>拼装细节交给 {@link PromptEngine}：主提示 / 世界书 / 作者注释 / 历史，
     * 这里只负责从库里取数据、并把统计数字带回去。
     */
    private PromptBundle buildPrompt(CharacterCard card, Long sessionId, Long cutoffMessageId) {
        ChatSession session = sessionMapper.findById(sessionId);
        String userName = "你";
        if (session != null && session.getUserId() != null) {
            User u = userMapper.findById(session.getUserId());
            if (u != null && u.getNickname() != null && !u.getNickname().isBlank()) {
                userName = u.getNickname();
            }
        }

        String style = "";
        if (card.getTags() != null && !card.getTags().isEmpty()) {
            style = card.getTags().stream().map(Tag::getName).filter(Objects::nonNull)
                    .collect(Collectors.joining("、"));
        } else if (card.getId() != null) {
            style = tagMapper.findByCharacterId(card.getId()).stream().map(Tag::getName)
                    .collect(Collectors.joining("、"));
        }

        int window = defaultContextWindow;
        ModelConfig cfg = aiModelService.resolveConfig();
        if (cfg.getContextWindowSize() != null && cfg.getContextWindowSize() > 0) {
            window = cfg.getContextWindowSize();
        }

        List<ChatMessage> recent = messageMapper.listRecent(sessionId, window);
        // 滑动重生时，要重写的那条消息本身（以及它之后的消息）不能进提示词，
        // 否则模型会看到自己上一版回答，变成「自我续写」而不是「重新回答这一问」
        if (cutoffMessageId != null) {
            recent.removeIf(m -> m.getId() != null && m.getId() >= cutoffMessageId);
        }
        Collections.reverse(recent);
        List<PromptInput.Turn> history = recent.stream()
                .filter(m -> !"system".equals(m.getRole()))
                .map(m -> new PromptInput.Turn(m.getRole(), m.getContent()))
                .toList();

        long userTurns = history.stream().filter(t -> "user".equals(t.role())).count();
        String authorNote = session == null ? null : session.getAuthorNote();
        int anDepth = session == null || session.getAnDepth() == null ? 0 : session.getAnDepth();
        int anFreq = session == null || session.getAnFrequency() == null ? 1 : session.getAnFrequency();

        List<WorldInfoEntry> entries = card.getId() == null
                ? List.of()
                : worldInfoMapper.findActiveForCharacter(card.getId(),
                        session == null ? null : session.getUserId());

        int maxOutput = card.getMaxTokens() == null ? 500 : card.getMaxTokens();

        PromptInput input = PromptInput.builder()
                .character(card.getName(), card.getDescription(), card.getPersonality(),
                        card.getBackgroundStory(), card.getExampleDialogue(), style)
                .user(userName)
                .history(history)
                .authorNote(authorNote, anDepth, anFreq, userTurns)
                .world(entries, worldInfoScanDepth)
                .budget(maxInputTokens, maxOutput)
                .build();

        PromptBundle bundle = promptEngine.build(input);
        // 会话总消息数只有这里知道，用来算「被窗口丢掉多少」
        int total = messageMapper.countBySession(sessionId);
        return bundle.withDroppedRounds(Math.max(total - bundle.usedRounds(), 0));
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

    private static Double toDouble(BigDecimal v) {
        return v == null ? null : v.doubleValue();
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
