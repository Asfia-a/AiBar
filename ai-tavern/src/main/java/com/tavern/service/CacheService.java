package com.tavern.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tavern.common.RedisConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

/**
 * 通用缓存封装，同时承担可视化模块需要的埋点。
 *
 * <p>每次 {@link #get} 都记录一次调用：命中与否、耗时、来源。管理端的「缓存命中对比」
 * 就是读这张流水，不是靠猜的。埋点写 Redis 列表而非 MySQL——它只在管理端读，
 * 写多读少，Redis 更合适。
 *
 * <p>Redis 不可用时静默降级为直接查库，不让缓存故障影响主流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheService {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    @Value("${tavern.cache.ttl-seconds:300}")
    private long ttlSeconds;

    /** 埋点流水最多留 500 条，够管理端画趋势了 */
    private static final int TRACE_MAX = 500;

    /**
     * 读缓存，未命中则回源并写回。
     *
     * @param name 业务名，用于埋点归类，如 hot-characters / tags / model-config
     */
    public <T> T get(String key, String name, Class<T> type, Supplier<T> loader) {
        long start = System.nanoTime();
        try {
            String json = redis.opsForValue().get(key);
            if (json != null && !json.isBlank()) {
                T value = objectMapper.readValue(json, type);
                trace(name, true, System.nanoTime() - start, json.length());
                return value;
            }
            T fresh = loader.get();
            String payload = objectMapper.writeValueAsString(fresh);
            redis.opsForValue().set(key, payload, Duration.ofSeconds(ttlSeconds));
            trace(name, false, System.nanoTime() - start, payload.length());
            return fresh;
        } catch (Exception e) {
            log.debug("缓存读取失败 key={}，回源: {}", key, e.getMessage());
            trace(name, false, System.nanoTime() - start, 0);
            return loader.get();
        }
    }

    /**
     * 读列表缓存。
     *
     * @param sizeOf 用于估算对象占用，埋点里记字节数近似值
     */
    public <T> List<T> getList(String key, String name, Class<T> elementType, Supplier<List<T>> loader) {
        long start = System.nanoTime();
        try {
            String json = redis.opsForValue().get(key);
            if (json != null && !json.isBlank()) {
                List<T> list = objectMapper.readValue(json,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, elementType));
                trace(name, true, System.nanoTime() - start, json.length());
                return list;
            }
            List<T> fresh = loader.get();
            String payload = objectMapper.writeValueAsString(fresh);
            redis.opsForValue().set(key, payload, Duration.ofSeconds(ttlSeconds));
            trace(name, false, System.nanoTime() - start, payload.length());
            return fresh;
        } catch (Exception e) {
            // 缓存不可用不该拖垮业务，直接回源
            log.debug("缓存读取失败 key={}，回源: {}", key, e.getMessage());
            trace(name, false, System.nanoTime() - start, 0);
            return loader.get();
        }
    }

    /** 主动失效。管理端改配置、创作者改角色后调用 */
    public void evict(String key) {
        try {
            redis.delete(key);
        } catch (Exception e) {
            log.debug("缓存失效失败 key={}: {}", key, e.getMessage());
        }
    }

    /** 按前缀批量失效 */
    public void evictByPrefix(String prefix) {
        try {
            var keys = redis.keys(prefix + "*");
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }
        } catch (Exception e) {
            log.debug("批量失效失败 prefix={}: {}", prefix, e.getMessage());
        }
    }

    public void put(String key, Object value) {
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(value), Duration.ofSeconds(ttlSeconds));
        } catch (Exception e) {
            log.debug("缓存写入失败 key={}: {}", key, e.getMessage());
        }
    }

    /**
     * 记一条埋点。Redis 不可用时静默丢弃——埋点本身不能成为故障源。
     *
     * @param costMicros 耗时，单位微秒
     */
    public void trace(String name, boolean hit, long costMicros, int payloadBytes) {
        try {
            String entry = objectMapper.writeValueAsString(java.util.Map.of(
                    "name", name,
                    "hit", hit,
                    "cost", costMicros / 1000.0,
                    "size", payloadBytes,
                    "at", System.currentTimeMillis()
            ));
            String key = RedisConstant.CACHE_TRACE;
            redis.opsForList().leftPush(key, entry);
            redis.opsForList().trim(key, 0, TRACE_MAX);
        } catch (Exception e) {
            log.trace("埋点写入失败: {}", e.getMessage());
        }
    }

    /** 读最近 n 条埋点，供管理端展示 */
    public List<String> recentTraces(long n) {
        try {
            var list = redis.opsForList().range(RedisConstant.CACHE_TRACE, 0, Math.max(n - 1, 0));
            return list == null ? List.of() : list;
        } catch (Exception e) {
            return List.of();
        }
    }

    /** 统计指定业务名的埋点，返回命中数、未命中数、平均耗时 */
    public java.util.Map<String, Object> stats(String name) {
        long hit = 0;
        long miss = 0;
        double hitCost = 0;
        double missCost = 0;
        for (String raw : recentTraces(TRACE_MAX)) {
            try {
                @SuppressWarnings("unchecked")
                var m = objectMapper.readValue(raw, java.util.Map.class);
                if (!name.equals(m.get("name"))) {
                    continue;
                }
                if (Boolean.TRUE.equals(m.get("hit"))) {
                    hit++;
                    hitCost += toDouble(m.get("cost"));
                } else {
                    miss++;
                    missCost += toDouble(m.get("cost"));
                }
            } catch (Exception ignored) {
                // 单条解析失败不影响整体统计
            }
        }
        long total = hit + miss;
        java.util.Map<String, Object> out = new java.util.HashMap<>();
        out.put("name", name);
        out.put("hit", hit);
        out.put("miss", miss);
        out.put("total", total);
        out.put("hitRate", total == 0 ? 0.0 : Math.round(hit * 10000.0 / total) / 100.0);
        out.put("avgHitCost", hit == 0 ? 0.0 : round(hitCost / hit));
        out.put("avgMissCost", miss == 0 ? 0.0 : round(missCost / miss));
        return out;
    }

    /** 清空埋点，管理端「重置统计」用 */
    public void clearTraces() {
        try {
            redis.delete(RedisConstant.CACHE_TRACE);
        } catch (Exception e) {
            log.debug("清空埋点失败: {}", e.getMessage());
        }
    }

    private static double toDouble(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0.0;
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
