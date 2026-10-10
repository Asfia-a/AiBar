package com.tavern.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tavern.common.RedisConstant;
import com.tavern.mapper.TokenLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 运行机制可视化：把系统内部平时看不见的行为记下来，供前端画图。
 *
 * <p>三张图对应三份数据：
 * <ul>
 *   <li>上下文占用条 —— {@link #recordContext} 记每次对话的窗口占用</li>
 *   <li>Token 明细 —— 读 {@code t_token_log}，按用途分类</li>
 *   <li>缓存命中折线 —— {@link CacheService} 的埋点流水</li>
 * </ul>
 *
 * <p>全部写 Redis 列表并裁剪长度。原因很直接：这些数据只在打开可视化页面时读一次，
 * 写却跟着每次对话发生。放 MySQL 会给高频写路径加负担，而这些数据过期就没意义了。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuntimeMetricsService {

    private final StringRedisTemplate redis;
    private final CacheService cacheService;
    private final TokenLogMapper tokenLogMapper;
    private final ObjectMapper objectMapper;

    /** 上下文采样保留条数 */
    private static final int MAX_SAMPLES = 300;
    /** MCP 调用时间线保留条数 */
    private static final int MAX_MCP = 200;

    /**
     * 记一次上下文裁剪。
     *
     * <p>{@code usage} 是占用比例：已用轮数 / 窗口上限。超过 1 说明触到了裁剪，
     * 这条曲线是「上下文占用条」和论文里那张对比图的共同数据源。
     */
    public void recordContext(ChatService.PromptBundle bundle) {
        try {
            double usage = bundle.window() == 0 ? 0 : (double) bundle.usedRounds() / bundle.window();
            Map<String, Object> m = new HashMap<>();
            m.put("at", System.currentTimeMillis());
            m.put("window", bundle.window());
            m.put("usedRounds", bundle.usedRounds());
            m.put("droppedRounds", bundle.droppedRounds());
            m.put("systemChars", bundle.systemChars());
            m.put("dialogueChars", bundle.dialogueChars());
            m.put("estimatedTokens", bundle.estimatedTokens());
            m.put("usage", Math.round(usage * 1000.0) / 1000.0);
            push(RedisConstant.METRIC_CONTEXT, objectMapper.writeValueAsString(m), MAX_SAMPLES);
        } catch (Exception e) {
            log.trace("上下文埋点失败: {}", e.getMessage());
        }
    }

    /** 记一次 MCP 工具调用，供「MCP 调用时间线」用 */
    public void recordMcpCall(String tool, long costMillis, boolean success) {
        try {
            Map<String, Object> m = new HashMap<>();
            m.put("at", System.currentTimeMillis());
            m.put("tool", tool);
            m.put("cost", costMillis);
            m.put("success", success);
            push(RedisConstant.METRIC_MCP, objectMapper.writeValueAsString(m), MAX_MCP);
        } catch (Exception e) {
            log.trace("MCP 埋点失败: {}", e.getMessage());
        }
    }

    /** 最近 n 次上下文采样，时间正序 */
    public List<Map<String, Object>> contextSamples(long n) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (String raw : reverseRange(RedisConstant.METRIC_CONTEXT, n)) {
            Map<String, Object> m = parse(raw);
            if (m != null) {
                out.add(m);
            }
        }
        out.sort(Comparator.comparingLong(x -> asLong(x.get("at"))));
        return out;
    }

    /** 最近 n 次 MCP 调用，时间正序 */
    public List<Map<String, Object>> mcpTimeline(long n) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (String raw : reverseRange(RedisConstant.METRIC_MCP, n)) {
            Map<String, Object> m = parse(raw);
            if (m != null) {
                out.add(m);
            }
        }
        out.sort(Comparator.comparingLong(x -> asLong(x.get("at"))));
        return out;
    }

    /**
     * 缓存命中对比。按业务名聚合埋点，前端画折线。
     *
     * <p>没有数据时返回空列表而不是报错——刚启动没数据是正常状态。
     */
    public List<Map<String, Object>> cacheComparison() {
        // 只列真正接了缓存的业务，避免图表上出现一条恒为 0 的假曲线
        List<String> names = List.of("hot-characters", "tags");
        List<Map<String, Object>> out = new ArrayList<>();
        for (String name : names) {
            Map<String, Object> stat = cacheService.stats(name);
            if (asLong(stat.get("total")) > 0) {
                out.add(stat);
            }
        }
        return out;
    }

    /**
     * 上下文概览：给「上下文占用条」顶部那排数字用。
     * 取最近一次采样，加上历史平均。
     */
    public Map<String, Object> contextOverview() {
        List<Map<String, Object>> samples = contextSamples(MAX_SAMPLES);
        Map<String, Object> out = new HashMap<>();
        if (samples.isEmpty()) {
            out.put("hasData", false);
            return out;
        }
        double sumUsage = 0;
        int maxWindow = 0;
        long lastAt = 0;
        Map<String, Object> last = samples.get(samples.size() - 1);
        for (Map<String, Object> s : samples) {
            sumUsage += toDouble(s.get("usage"));
            maxWindow = Math.max(maxWindow, (int) asLong(s.get("window")));
            lastAt = Math.max(lastAt, asLong(s.get("at")));
        }
        out.put("hasData", true);
        out.put("sampleCount", samples.size());
        out.put("avgUsage", Math.round(sumUsage / samples.size() * 1000.0) / 1000.0);
        out.put("maxWindow", maxWindow);
        out.put("lastAt", lastAt);
        out.put("current", last);
        return out;
    }

    /**
     * Token 明细。按用途分组 + 最费 token 的会话。
     *
     * <p>从 {@code t_token_log} 实时聚合，不走 Redis——这是需要准确账目的一环，
     * 不能像采样指标那样容忍丢失。
     */
    public Map<String, Object> tokenDetail(int topN) {
        Map<String, Object> out = new HashMap<>();
        out.put("byType", tokenLogMapper.breakdownByType());
        out.put("topSessions", tokenLogMapper.topSessions(Math.min(Math.max(topN, 1), 50)));
        out.put("todayTokens", tokenLogMapper.sumToday());
        return out;
    }

    /** 清空指标，仅测试与管理端重置用 */
    public void clear() {
        try {
            redis.delete(List.of(RedisConstant.METRIC_CONTEXT, RedisConstant.METRIC_MCP));
        } catch (Exception e) {
            log.debug("清空指标失败: {}", e.getMessage());
        }
    }

    private void push(String key, String value, int max) {
        redis.opsForList().leftPush(key, value);
        redis.opsForList().trim(key, 0, max);
        // 指标没有长期价值，过期自动清，避免占内存
        redis.expire(key, Duration.ofDays(3));
    }

    /** 取最近 n 条（list 是新在前，这里翻转成时间正序） */
    private List<String> reverseRange(String key, long n) {
        try {
            var list = redis.opsForList().range(key, 0, Math.max(n - 1, 0));
            if (list == null || list.isEmpty()) {
                return List.of();
            }
            List<String> copy = new ArrayList<>(list);
            java.util.Collections.reverse(copy);
            return copy;
        } catch (Exception e) {
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parse(String raw) {
        try {
            return objectMapper.readValue(raw, Map.class);
        } catch (Exception e) {
            return null;
        }
    }

    private static long asLong(Object o) {
        return o instanceof Number n ? n.longValue() : 0L;
    }

    private static double toDouble(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0.0;
    }
}
