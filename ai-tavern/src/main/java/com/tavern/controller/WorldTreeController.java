package com.tavern.controller;

import com.tavern.common.Result;
import com.tavern.dto.WorldTreeRequest;
import com.tavern.interceptor.UserContext;
import com.tavern.service.WorldTreeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class WorldTreeController {

    private final WorldTreeService worldTreeService;

    /** 世界广场：默认只看已发布 */
    @GetMapping("/worlds")
    public Result<?> list(@RequestParam(required = false) String keyword,
                          @RequestParam(required = false) Long creatorId,
                          @RequestParam(required = false) Integer status,
                          @RequestParam(defaultValue = "1") int page,
                          @RequestParam(defaultValue = "12") int size) {
        Long me = UserContext.getUserId();
        Integer st = status;
        if (creatorId != null && me != null && creatorId.equals(me)) {
            // 看自己的：可按 status 筛，不传则全部状态
            st = status;
        } else if (creatorId == null) {
            st = WorldTreeService.STATUS_PUBLISHED;
        } else {
            // 看别人的：只公开
            st = WorldTreeService.STATUS_PUBLISHED;
        }
        return Result.ok(worldTreeService.list(st, creatorId, keyword, page, size));
    }

    @GetMapping("/worlds/hot")
    public Result<?> hot(@RequestParam(defaultValue = "12") int limit) {
        return Result.ok(worldTreeService.hot(limit));
    }

    @GetMapping("/worlds/{id}")
    public Result<?> detail(@PathVariable Long id) {
        return Result.ok(worldTreeService.detail(id, UserContext.getUserId()));
    }

    @PostMapping("/worlds")
    public Result<?> create(@Valid @RequestBody WorldTreeRequest request,
                            @RequestParam(defaultValue = "false") boolean publish) {
        return Result.ok(worldTreeService.create(UserContext.getUserId(), request, publish));
    }

    @PutMapping("/worlds/{id}")
    public Result<?> update(@PathVariable Long id, @Valid @RequestBody WorldTreeRequest request) {
        return Result.ok(worldTreeService.update(UserContext.getUserId(), id, request));
    }

    @PostMapping("/worlds/{id}/publish")
    public Result<?> publish(@PathVariable Long id) {
        worldTreeService.publish(UserContext.getUserId(), id);
        return Result.ok();
    }

    @PostMapping("/worlds/{id}/offline")
    public Result<?> offline(@PathVariable Long id) {
        worldTreeService.offline(UserContext.getUserId(), id);
        return Result.ok();
    }

    @DeleteMapping("/worlds/{id}")
    public Result<?> delete(@PathVariable Long id) {
        worldTreeService.delete(UserContext.getUserId(), id);
        return Result.ok(Map.of("ok", true));
    }
}
