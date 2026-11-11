package com.tavern.controller;

import com.tavern.common.Result;
import com.tavern.interceptor.UserContext;
import com.tavern.service.NarrativePromptService;
import com.tavern.service.NarrativeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/narrative")
@RequiredArgsConstructor
public class NarrativeController {

    private final NarrativeService narrativeService;

    /** 可改写成作品的会话列表 */
    @GetMapping("/sessions")
    public Result<?> sessions() {
        return Result.ok(narrativeService.listSourceSessions(UserContext.getUserId()));
    }

    /** SSE 流式生成，输入 sessionId + 写作参数 */
    @PostMapping(value = "/generate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generate(@RequestBody Map<String, Object> body) {
        Long sessionId = Long.valueOf(String.valueOf(body.get("sessionId")));
        NarrativePromptService.Request req = new NarrativePromptService.Request(
                sessionId,
                str(body.get("formType")),
                str(body.get("style")),
                str(body.get("length")),
                str(body.get("extraInstruction")),
                str(body.get("modelName")));
        return narrativeService.generate(UserContext.getUserId(), req);
    }

    /**
     * 同步改写，供 MCP 工具调用。
     *
     * <p>与 {@link #generate} 的区别只在于返回方式：这里是等生成完一次性返回完整正文，
     * 因为 MCP 协议是一次性请求-响应，拿不到 SSE 增量流。
     */
    @PostMapping("/rewrite")
    public Result<?> rewrite(@RequestBody Map<String, Object> body) {
        Long sessionId = Long.valueOf(String.valueOf(body.get("sessionId")));
        NarrativePromptService.Request req = new NarrativePromptService.Request(
                sessionId,
                str(body.get("formType")),
                str(body.get("style")),
                str(body.get("length")),
                str(body.get("extraInstruction")),
                str(body.get("modelName")));
        return Result.ok(narrativeService.rewriteSync(UserContext.getUserId(), req));
    }

    @GetMapping("/works")
    public Result<?> works() {
        return Result.ok(narrativeService.listMine(UserContext.getUserId()));
    }

    @GetMapping("/works/{id}")
    public Result<?> detail(@PathVariable Long id) {
        return Result.ok(narrativeService.detail(UserContext.getUserId(), id));
    }

    @PutMapping("/works/{id}")
    public Result<?> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Result.ok(narrativeService.updateContent(
                UserContext.getUserId(), id, str(body.get("title")), str(body.get("content"))));
    }

    @PostMapping("/works/{id}/publish")
    public Result<?> publish(@PathVariable Long id) {
        return Result.ok(narrativeService.publish(UserContext.getUserId(), id));
    }

    @PostMapping("/works/{id}/unpublish")
    public Result<?> unpublish(@PathVariable Long id) {
        narrativeService.unpublish(UserContext.getUserId(), id);
        return Result.ok();
    }

    @DeleteMapping("/works/{id}")
    public Result<?> delete(@PathVariable Long id) {
        narrativeService.delete(UserContext.getUserId(), id);
        return Result.ok();
    }

    /** 导出 txt / md，直接返回文件流 */
    @GetMapping("/works/{id}/export")
    public ResponseEntity<byte[]> export(@PathVariable Long id, @RequestParam(defaultValue = "txt") String format) {
        Long userId = UserContext.getUserId();
        var work = narrativeService.detail(userId, id);
        String text = narrativeService.export(userId, id, format);
        boolean md = "md".equalsIgnoreCase(format);
        String encoded = URLEncoder.encode(sanitize(work.getTitle()) + (md ? ".md" : ".txt"), StandardCharsets.UTF_8)
                .replace("+", "%20");
        MediaType type = md
                ? MediaType.parseMediaType("text/markdown; charset=UTF-8")
                : MediaType.parseMediaType("text/plain; charset=UTF-8");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(type)
                .body(text.getBytes(StandardCharsets.UTF_8));
    }

    /** 社区广场：已发布且审核通过的公开作品 */
    @GetMapping("/published")
    public Result<?> published() {
        return Result.ok(narrativeService.listPublished());
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String sanitize(String s) {
        if (s == null || s.isBlank()) {
            return "narrative";
        }
        return s.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
