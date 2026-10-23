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
        return Result.ok(chatService.createSession(UserContext.getUserId(), characterId, title));
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
        return chatService.stream(UserContext.getUserId(), sessionId, characterId, message);
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
    public SseEmitter swipe(@PathVariable Long id) {
        return chatService.swipe(UserContext.getUserId(), id);
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
}
