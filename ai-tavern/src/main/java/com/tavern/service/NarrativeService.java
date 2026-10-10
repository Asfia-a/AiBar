package com.tavern.service;

import com.tavern.common.BusinessException;
import com.tavern.common.ErrorCode;
import com.tavern.config.BillingProperties;
import com.tavern.entity.ChatSession;
import com.tavern.entity.CharacterCard;
import com.tavern.entity.NarrativeWork;
import com.tavern.entity.TokenLog;
import com.tavern.entity.User;
import com.tavern.mapper.ChatSessionMapper;
import com.tavern.mapper.CharacterMapper;
import com.tavern.mapper.NarrativeWorkMapper;
import com.tavern.mapper.TokenLogMapper;
import com.tavern.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 叙事生成主服务：SSE 流式改写 + 积分计费 + 作品落库 + 导出。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NarrativeService {

    private final NarrativePromptService promptService;
    private final NarrativeWorkMapper workMapper;
    private final ChatSessionMapper sessionMapper;
    private final CharacterMapper characterMapper;
    private final UserMapper userMapper;
    private final TokenLogMapper tokenLogMapper;
    private final ContentAuditService contentAuditService;
    private final AiModelService aiModelService;
    private final BillingProperties billingProperties;

    private final ExecutorService sseExecutor = Executors.newCachedThreadPool();

    /** 可作为叙事来源的会话：至少 4 条对话、消息量够改写 */
    public List<ChatSession> listSourceSessions(Long userId) {
        return sessionMapper.listByUser(userId).stream()
                .filter(s -> s.getMessageCount() == null || s.getMessageCount() >= 4)
                .toList();
    }

    public SseEmitter generate(Long userId, NarrativePromptService.Request req) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (user.getPoints() == null || user.getPoints() <= 0) {
            throw new BusinessException(ErrorCode.TOKEN_INSUFFICIENT);
        }

        ChatSession session = sessionMapper.findById(req.sessionId());
        if (session == null || !userId.equals(session.getUserId())) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }

        NarrativePromptService.PromptBundle bundle = promptService.build(userId, req);

        SseEmitter emitter = new SseEmitter(300_000L);
        emitter.onTimeout(emitter::complete);
        emitter.onError(e -> log.warn("narrative SSE error: {}", e.toString()));
        sseExecutor.execute(() -> {
            try {
                doGenerate(userId, req, session, bundle, emitter);
            } catch (Exception e) {
                log.error("narrative generate error", e);
                try {
                    emitter.send(SseEmitter.event().name("error")
                            .data(e.getMessage() == null ? "生成失败" : e.getMessage(),
                                    org.springframework.http.MediaType.TEXT_PLAIN));
                } catch (IOException ignored) {
                }
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    private void doGenerate(Long userId, NarrativePromptService.Request req, ChatSession session,
                            NarrativePromptService.PromptBundle bundle, SseEmitter emitter) throws IOException {
        CharacterCard card = session.getCharacterId() == null ? null : characterMapper.findById(session.getCharacterId());

        // meta 事件把统计信息先推给前端，用于展示「用了多少素材」
        emitter.send(SseEmitter.event().name("meta").data(
                "{\"messageCount\":" + bundle.messageCount()
                        + ",\"dialogueChars\":" + bundle.dialogueChars() + "}",
                org.springframework.http.MediaType.TEXT_PLAIN));

        StringBuilder full = new StringBuilder();
        AiModelService.StreamResult result = aiModelService.streamChat(
                bundle.messages(), 0.85, 0.92, 3000, delta -> {
                    if (delta == null || delta.isEmpty()) {
                        return;
                    }
                    full.append(delta);
                    try {
                        emitter.send(SseEmitter.event().data(delta, org.springframework.http.MediaType.TEXT_PLAIN));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

        String content = full.toString().trim();
        if (!StringUtils.hasText(content)) {
            emitter.send(SseEmitter.event().name("error").data("AI 返回空内容", org.springframework.http.MediaType.TEXT_PLAIN));
            emitter.complete();
            return;
        }

        NarrativeWork work = persist(userId, req, session, card, content, result.totalTokens(), bundle.messageCount());
        emitter.send(SseEmitter.event().name("done")
                .data("{\"id\":" + work.getId() + "}", org.springframework.http.MediaType.TEXT_PLAIN));
        emitter.send(SseEmitter.event().data("[DONE]", org.springframework.http.MediaType.TEXT_PLAIN));
        emitter.complete();
    }

    /**
     * 同步改写，不走 SSE。
     *
     * <p>给 MCP 工具用——MCP 是一次性请求-响应，拿不到 SSE 的增量流，
     * 只能等生成完一次性返回。Web 端仍然走 {@link #generate} 的流式。
     */
    @Transactional
    public NarrativeWork rewriteSync(Long userId, NarrativePromptService.Request req) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (user.getPoints() == null || user.getPoints() <= 0) {
            throw new BusinessException(ErrorCode.TOKEN_INSUFFICIENT);
        }
        NarrativePromptService.PromptBundle bundle = promptService.build(userId, req);
        ChatSession session = sessionMapper.findById(req.sessionId());
        CharacterCard card = session.getCharacterId() == null ? null
                : characterMapper.findById(session.getCharacterId());

        long start = System.currentTimeMillis();
        String content;
        try {
            content = aiModelService.chatCompletion(bundle.messages(), 0.85, 3000);
        } catch (Exception e) {
            log.warn("同步改写失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.AI_ERROR);
        }
        content = content == null ? "" : content.trim();
        if (!StringUtils.hasText(content)) {
            throw new BusinessException("AI 返回空内容");
        }
        int tokens = (int) ((content.length() / 1.5) + (System.currentTimeMillis() - start) / 100);
        return persist(userId, req, session, card, content, tokens, bundle.messageCount());
    }

    @Transactional
    protected NarrativeWork persist(Long userId, NarrativePromptService.Request req, ChatSession session,
                                    CharacterCard card, String content, int apiTokens, int sourceCount) {
        int pointsCost = billingProperties.toPoints(apiTokens);
        User user = userMapper.findById(userId);
        long before = user.getPoints() == null ? 0L : user.getPoints();
        int deducted = userMapper.deductPoints(userId, pointsCost);
        if (deducted == 0) {
            pointsCost = (int) Math.min(Integer.MAX_VALUE, before);
            if (pointsCost > 0) {
                userMapper.deductPoints(userId, pointsCost);
            }
        }
        long after = Math.max(0, before - pointsCost);

        NarrativeWork work = new NarrativeWork();
        work.setUserId(userId);
        work.setSessionId(session.getId());
        work.setCharacterId(session.getCharacterId());
        work.setTitle(buildTitle(req, card, session, content));
        work.setSummary(buildSummary(content));
        work.setContent(content);
        work.setFormType(defaultStr(req.formType(), "novel"));
        work.setStyle(defaultStr(req.style(), "literary"));
        work.setLength(defaultStr(req.length(), "medium"));
        work.setExtraInstruction(req.extraInstruction());
        work.setWordCount(content.length());
        work.setSourceMessageCount(sourceCount);
        work.setTokens(pointsCost);
        work.setStatus(0);
        work.setAuditStatus(0);
        workMapper.insert(work);

        // 计费流水复用 t_token_log，session_id 指向来源会话，type=1 消费
        TokenLog log = new TokenLog();
        log.setUserId(userId);
        log.setSessionId(session.getId());
        log.setTokensUsed(pointsCost);
        log.setBalanceBefore(before);
        log.setBalanceAfter(after);
        log.setType(1);
        tokenLogMapper.insert(log);

        return workMapper.findById(work.getId());
    }

    public List<NarrativeWork> listMine(Long userId) {
        return workMapper.listByUser(userId);
    }

    public NarrativeWork detail(Long userId, Long id) {
        NarrativeWork work = workMapper.findById(id);
        if (work == null || !userId.equals(work.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return work;
    }

    public NarrativeWork updateContent(Long userId, Long id, String title, String content) {
        NarrativeWork work = detail(userId, id);
        if (!StringUtils.hasText(content)) {
            throw new BusinessException("作品内容不能为空");
        }
        NarrativeWork patch = new NarrativeWork();
        patch.setId(work.getId());
        patch.setUserId(userId);
        patch.setTitle(StringUtils.hasText(title) ? title.trim() : work.getTitle());
        patch.setContent(content.trim());
        patch.setSummary(buildSummary(content.trim()));
        patch.setWordCount(content.trim().length());
        workMapper.updateContent(patch);
        return workMapper.findById(work.getId());
    }

    /**
     * 发布作品。发布前先做内容审核，通过才进社区；
     * 未通过的保留草稿状态并返回原因，避免作者白忙一场。
     */
    public NarrativeWork publish(Long userId, Long id) {
        NarrativeWork work = detail(userId, id);
        if (work.getStatus() != null && work.getStatus() == 1) {
            throw new BusinessException("作品已发布");
        }
        List<String> hit = contentAuditService.auditSync(work.getContent());
        if (!hit.isEmpty()) {
            workMapper.updatePublish(id, userId, 0, 2);
            throw new BusinessException("内容包含敏感词，发布未通过：" + String.join("、", hit));
        }
        workMapper.updatePublish(id, userId, 1, 1);
        return workMapper.findById(id);
    }

    public void unpublish(Long userId, Long id) {
        detail(userId, id);
        workMapper.updatePublish(id, userId, 0, 0);
    }

    public void delete(Long userId, Long id) {
        detail(userId, id);
        workMapper.deleteByUser(id, userId);
    }

    /** 社区广场：已发布且审核通过 */
    public List<NarrativeWork> listPublished() {
        return workMapper.listPublished();
    }

    /** 导出为纯文本 / Markdown，供前端生成下载 */
    public String export(Long userId, Long id, String format) {
        NarrativeWork work = detail(userId, id);
        StringBuilder sb = new StringBuilder();
        if ("md".equalsIgnoreCase(format)) {
            sb.append("# ").append(work.getTitle()).append("\n\n");
            sb.append("> 形式：").append(formLabel(work.getFormType()))
              .append(" ｜ 字数：").append(work.getWordCount())
              .append(" ｜ 来源对话：").append(work.getSourceMessageCount()).append(" 条\n\n");
            sb.append(toMarkdownBody(work.getContent())).append("\n");
        } else {
            sb.append(work.getTitle()).append("\n\n");
            sb.append(toPlainText(work.getContent()));
        }
        return sb.toString();
    }

    /** 正文里若有 Markdown 标题层级，导出 md 时保留 */
    private String toMarkdownBody(String content) {
        if (content == null) {
            return "";
        }
        return content.replaceAll("(?m)^(第[一二三四五六七八九十百零0-9]+[章节].*)$", "## $1")
                .replaceAll("(?m)^(场景[一二三四五六七八九十0-9]+.*)$", "### $1");
    }

    private String toPlainText(String content) {
        if (content == null) {
            return "";
        }
        return content.replaceAll("(?m)^#{1,6}\\s*", "").replaceAll("(?m)^(第[一二三四五六七八九十百零0-9]+[章节].*)$", "$1\n");
    }

    /** 标题：优先用用户额外指定的，其次角色名 + 形式 */
    private String buildTitle(NarrativePromptService.Request req, CharacterCard card,
                              ChatSession session, String content) {
        if (StringUtils.hasText(req.extraInstruction())) {
            String first = req.extraInstruction().trim().split("\\r?\\n")[0];
            if (first.length() >= 2 && first.length() <= 30) {
                return first;
            }
        }
        String who = card != null && StringUtils.hasText(card.getName()) ? card.getName() : "我";
        String suffix = switch (defaultStr(req.formType(), "novel")) {
            case "script" -> "剧本";
            case "outline" -> "大纲";
            default -> "小说";
        };
        String sessionTitle = StringUtils.hasText(session.getTitle()) ? session.getTitle() : "";
        return truncate(who + "与" + sessionTitle + "的" + suffix, 100);
    }

    private String buildSummary(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String flat = content.replaceAll("\\s+", " ").trim();
        return truncate(flat, 180);
    }

    private static String formLabel(String formType) {
        return switch (defaultStr(formType, "novel")) {
            case "script" -> "剧本";
            case "outline" -> "大纲";
            default -> "小说";
        };
    }

    private static String defaultStr(String s, String def) {
        return StringUtils.hasText(s) ? s : def;
    }

    private static String truncate(String s, int n) {
        if (s == null) {
            return "";
        }
        return s.length() <= n ? s : s.substring(0, n);
    }
}
