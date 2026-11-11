package com.tavern.controller;

import com.tavern.common.Result;
import com.tavern.interceptor.UserContext;
import com.tavern.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @GetMapping("/sessions")
    public Result<?> sessions() {
        return Result.ok(chatService.listSessions(UserContext.getUserId()));
    }

    @PostMapping("/sessions")
    public Result<?> createSession(@RequestBody Map<String, Object> body) {
        Long characterId = Long.valueOf(body.get("characterId").toString());
        String title = body.get("title") == null ? null : body.get("title").toString();
        String modelName = body.get("modelName") == null ? null : body.get("modelName").toString();
        return Result.ok(chatService.createSession(UserContext.getUserId(), characterId, title, modelName));
    }

    /** 聊天可用模型（仅 id/展示名，不含 API Key / Base URL） */
    @GetMapping("/models")
    public Result<?> models() {
        return Result.ok(chatService.listModels());
    }

    @PutMapping("/sessions/{id}/model")
    public Result<?> saveSessionModel(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String modelName = body.get("modelName") == null ? "" : body.get("modelName").toString();
        return Result.ok(chatService.saveSessionModel(UserContext.getUserId(), id, modelName));
    }

    @DeleteMapping("/sessions/{id}")
    public Result<?> deleteSession(@PathVariable Long id) {
        chatService.deleteSession(UserContext.getUserId(), id);
        return Result.ok();
    }

    @GetMapping("/sessions/{id}/messages")
    public Result<?> messages(@PathVariable Long id) {
        return Result.ok(chatService.listMessages(UserContext.getUserId(), id));
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam(required = false) Long sessionId,
                             @RequestParam(required = false) Long characterId,
                             @RequestParam String message) {
        return chatService.stream(UserContext.getUserId(), sessionId, characterId, message);
    }

    /** 兼容前端 POST 会话消息流式发送 */
    @PostMapping(value = "/sessions/{sessionId}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamPost(@PathVariable Long sessionId, @RequestBody Map<String, Object> body) {
        String message = body.get("message") == null ? body.getOrDefault("content", "").toString() : body.get("message").toString();
        Long characterId = body.get("characterId") == null ? null : Long.valueOf(body.get("characterId").toString());
        Boolean thinking = body.get("thinking") == null ? null : Boolean.valueOf(body.get("thinking").toString());
        return chatService.stream(UserContext.getUserId(), sessionId, characterId, message, thinking);
    }

    @PostMapping(value = "/messages/{id}/regenerate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter regenerate(@PathVariable Long id) {
        return chatService.regenerate(UserContext.getUserId(), id);
    }

    @PostMapping(value = "/sessions/{sessionId}/regenerate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter regenerateBySession(@PathVariable Long sessionId, @RequestBody Map<String, Object> body) {
        Long messageId = Long.valueOf(body.get("messageId").toString());
        return chatService.regenerate(UserContext.getUserId(), messageId);
    }

    /** 再生成一版（滑动重生）：旧版本保留，可左右切回来 */
    @PostMapping(value = "/messages/{id}/swipe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter swipe(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        Boolean thinking = body == null || body.get("thinking") == null
                ? null
                : Boolean.valueOf(body.get("thinking").toString());
        return chatService.swipe(UserContext.getUserId(), id, thinking);
    }

    /** 切到第 index 版（从 0 开始），不调模型不扣积分 */
    @PostMapping("/messages/{id}/swipe/select")
    public Result<?> selectSwipe(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        int index = body.get("index") == null ? 0 : Integer.parseInt(body.get("index").toString());
        return Result.ok(chatService.selectSwipe(UserContext.getUserId(), id, index));
    }

    @PutMapping("/messages/{id}")
    public Result<?> editMessage(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String content = body.get("content") == null ? "" : body.get("content").toString();
        return Result.ok(chatService.editMessage(UserContext.getUserId(), id, content));
    }

    /** 以这条消息为终点复制出一段新会话 */
    @PostMapping("/messages/{id}/branch")
    public Result<?> branch(@PathVariable Long id) {
        return Result.ok(chatService.branch(UserContext.getUserId(), id));
    }

    /** 批量删除会话里的消息：删掉后不再进入上下文 */
    @PostMapping("/sessions/{sessionId}/messages/delete")
    public Result<?> deleteMessages(@PathVariable Long sessionId, @RequestBody Map<String, Object> body) {
        Object raw = body.get("ids");
        java.util.List<Long> ids = new java.util.ArrayList<>();
        if (raw instanceof java.util.List<?> list) {
            for (Object o : list) {
                if (o != null) {
                    ids.add(Long.valueOf(o.toString()));
                }
            }
        }
        return Result.ok(chatService.deleteMessages(UserContext.getUserId(), sessionId, ids));
    }

    /**
     * 删除一轮：以某条消息为锚点，连同配对的用户/助手消息一起删掉，
     * 之后不会再进入提示词上下文。
     */
    @PostMapping("/sessions/{sessionId}/messages/{messageId}/delete-round")
    public Result<?> deleteRound(@PathVariable Long sessionId, @PathVariable Long messageId) {
        return Result.ok(chatService.deleteRound(UserContext.getUserId(), sessionId, messageId));
    }

    @PostMapping("/messages/{id}/collect")
    public Result<?> collect(@PathVariable Long id) {
        chatService.collectMessage(UserContext.getUserId(), id);
        return Result.ok();
    }

    @GetMapping("/search")
    public Result<?> search(@RequestParam String keyword,
                            @RequestParam(defaultValue = "1") int page,
                            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(chatService.search(UserContext.getUserId(), keyword, page, size));
    }

    @GetMapping("/sessions/{id}/author-note")
    public Result<?> authorNote(@PathVariable Long id) {
        return Result.ok(chatService.authorNote(UserContext.getUserId(), id));
    }

    @PutMapping("/sessions/{id}/author-note")
    public Result<?> saveAuthorNote(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String note = body.get("note") == null ? "" : body.get("note").toString();
        Integer depth = body.get("depth") == null ? null : Integer.valueOf(body.get("depth").toString());
        Integer frequency = body.get("frequency") == null ? null : Integer.valueOf(body.get("frequency").toString());
        return Result.ok(chatService.saveAuthorNote(UserContext.getUserId(), id, note, depth, frequency));
    }

    @GetMapping("/sessions/{id}/persona")
    public Result<?> persona(@PathVariable Long id) {
        return Result.ok(chatService.persona(UserContext.getUserId(), id));
    }

    @PutMapping("/sessions/{id}/persona")
    public Result<?> savePersona(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String name = body.get("personaName") == null
                ? (body.get("name") == null ? "" : body.get("name").toString())
                : body.get("personaName").toString();
        String desc = body.get("personaDesc") == null
                ? (body.get("desc") == null ? "" : body.get("desc").toString())
                : body.get("personaDesc").toString();
        return Result.ok(chatService.savePersona(UserContext.getUserId(), id, name, desc));
    }

    @GetMapping("/sessions/{id}/memory")
    public Result<?> memory(@PathVariable Long id) {
        return Result.ok(chatService.memory(UserContext.getUserId(), id));
    }

    @PutMapping("/sessions/{id}/memory")
    public Result<?> saveMemory(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String summary = body.containsKey("summary")
                ? (body.get("summary") == null ? "" : body.get("summary").toString())
                : null;
        Boolean enabled = body.get("enabled") == null ? null : Boolean.valueOf(body.get("enabled").toString());
        boolean clearUntil = Boolean.TRUE.equals(body.get("clearUntil"));
        return Result.ok(chatService.saveMemory(UserContext.getUserId(), id, summary, enabled, clearUntil));
    }

    @PostMapping("/sessions/{id}/memory/refresh")
    public Result<?> refreshMemory(@PathVariable Long id) {
        return Result.ok(chatService.refreshMemory(UserContext.getUserId(), id));
    }
}
