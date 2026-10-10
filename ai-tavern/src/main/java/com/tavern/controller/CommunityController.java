package com.tavern.controller;

import com.tavern.common.Result;
import com.tavern.interceptor.UserContext;
import com.tavern.service.CommunityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/community")
@RequiredArgsConstructor
public class CommunityController {

    private final CommunityService communityService;

    /** 广场列表。sort=latest 最新 / hot 最热 */
    @GetMapping("/works")
    public Result<?> works(@RequestParam(defaultValue = "latest") String sort,
                           @RequestParam(defaultValue = "1") int page,
                           @RequestParam(defaultValue = "12") int size) {
        return Result.ok(communityService.works(UserContext.getUserId(), sort, page, size));
    }

    /** 作品详情。未过审仅作者可见 */
    @GetMapping("/works/{id}")
    public Result<?> detail(@PathVariable Long id) {
        return Result.ok(communityService.detail(UserContext.getUserId(), id));
    }

    @PostMapping("/works/{id}/like")
    public Result<?> like(@PathVariable Long id) {
        return Result.ok(communityService.toggleLike(UserContext.getUserId(), id));
    }

    @PostMapping("/works/{id}/collect")
    public Result<?> collect(@PathVariable Long id) {
        return Result.ok(communityService.toggleCollect(UserContext.getUserId(), id));
    }

    @GetMapping("/works/{id}/comments")
    public Result<?> comments(@PathVariable Long id,
                              @RequestParam(defaultValue = "1") int page,
                              @RequestParam(defaultValue = "20") int size) {
        return Result.ok(communityService.comments(id, page, size));
    }

    @PostMapping("/works/{id}/comments")
    public Result<?> addComment(@PathVariable Long id, @RequestBody java.util.Map<String, String> body) {
        return Result.ok(communityService.addComment(UserContext.getUserId(), id, body.get("content")));
    }

    @DeleteMapping("/comments/{commentId}")
    public Result<?> deleteComment(@PathVariable Long commentId) {
        communityService.deleteComment(UserContext.getUserId(), commentId);
        return Result.ok();
    }

    /** 互动记录，供管理端追溯刷量 */
    @GetMapping("/works/{id}/records")
    public Result<?> records(@PathVariable Long id) {
        return Result.ok(communityService.records(id));
    }
}
