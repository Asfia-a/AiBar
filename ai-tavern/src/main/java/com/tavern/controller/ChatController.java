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
}
