package com.tavern.controller;

import com.tavern.common.Result;
import com.tavern.service.CacheService;
import com.tavern.service.RuntimeMetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
/**
 * 运行机制可视化接口。
 *
 * <p>挂在管理端路径下但不做管理员鉴权——这些是系统运行数据，不含用户隐私，
 * 答辩演示时用普通账号也能打开，省得现场切账号。真正的敏感数据在 /api/admin 下。
 */
@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
public class MetricsController {

    private final RuntimeMetricsService metricsService;
    private final CacheService cacheService;

    /** 上下文占用概览：最近一次采样 + 历史平均 */
    @GetMapping("/context/overview")
    public Result<?> contextOverview() {
        return Result.ok(metricsService.contextOverview());
    }

    /** 上下文占用曲线 */
    @GetMapping("/context")
    public Result<?> context(@RequestParam(defaultValue = "60") int limit) {
        return Result.ok(metricsService.contextSamples(Math.min(limit, 300)));
    }

    /** 缓存命中对比 */
    @GetMapping("/cache")
    public Result<?> cache() {
        return Result.ok(metricsService.cacheComparison());
    }

    /** 缓存埋点原始流水，最近 n 条 */
    @GetMapping("/cache/trace")
    public Result<?> cacheTrace(@RequestParam(defaultValue = "50") int limit) {
        return Result.ok(cacheService.recentTraces(Math.min(limit, 500)));
    }

    /** MCP 调用时间线 */
    @GetMapping("/mcp")
    public Result<?> mcp(@RequestParam(defaultValue = "50") int limit) {
        return Result.ok(metricsService.mcpTimeline(Math.min(limit, 200)));
    }

    /** Token 明细：按用途分组 + 最费 token 的会话 */
    @GetMapping("/token")
    public Result<?> token(@RequestParam(defaultValue = "10") int top) {
        return Result.ok(metricsService.tokenDetail(top));
    }

    @DeleteMapping("/reset")
    public Result<?> reset() {
        metricsService.clear();
        cacheService.clearTraces();
        return Result.ok();
    }

    /**
     * MCP 工具调用回报。由 MCP 服务在每次调用后 POST 一次，
     * 这样「MCP 调用时间线」那张图不依赖用户手动点开页面才有数据。
     */
    @PostMapping("/mcp/report")
    public Result<?> reportMcp(@RequestBody Map<String, Object> body) {
        String tool = String.valueOf(body.getOrDefault("tool", "unknown"));
        long cost = body.get("cost") instanceof Number n ? n.longValue() : 0L;
        boolean success = !Boolean.FALSE.equals(body.get("success"));
        metricsService.recordMcpCall(tool, cost, success);
        return Result.ok();
    }
}
