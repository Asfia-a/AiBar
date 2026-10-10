package com.tavern.service;

import com.tavern.common.RedisConstant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 频控与去重的行为测试。
 *
 * <p>不引 Spring 容器，直接 mock {@link StringRedisTemplate}，用内存 Map 模拟计数器语义，
 * 这样测的是 RateLimitService 自己的判断逻辑，不受环境里的 Redis 影响。
 */
@DisplayName("社区频控与去重")
class RateLimitServiceTest {

    /** 用 Map 冒充 Redis 的计数器行为：increment 自增、setIfAbsent 仅首次成功 */
    private StringRedisTemplate mockRedis(Map<String, Long> counters, Map<String, Boolean> dedup) {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);

        when(ops.increment(anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            return counters.merge(key, 1L, Long::sum);
        });
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            return dedup.putIfAbsent(key, Boolean.TRUE) == null;
        });
        when(ops.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class))).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            return dedup.putIfAbsent(key, Boolean.TRUE) == null;
        });
        when(redis.delete(anyString())).thenAnswer(inv -> dedup.remove(inv.getArgument(0)) != null);
        when(redis.getExpire(anyString(), any(TimeUnit.class)))
                .thenReturn((long) Math.max(dedup.size() % 30, 1));
        return redis;
    }

    @Test
    @DisplayName("窗口内连续点赞应在上限处被拦截")
    void likeLimitEnforced() {
        var counters = new HashMap<String, Long>();
        var service = new RateLimitService(mockRedis(counters, new HashMap<>()));

        int allowed = 0;
        for (int i = 0; i < 30; i++) {
            if (service.allow(RedisConstant.RATE_LIKE_PREFIX, 1L)) {
                allowed++;
            }
        }
        // 点赞窗口上限 20，第 21 次起应被拒
        assertEquals(20, allowed);
        assertFalse(service.allow(RedisConstant.RATE_LIKE_PREFIX, 1L));
    }

    @Test
    @DisplayName("评论上限低于点赞，且独立计数")
    void commentLimitStricter() {
        var counters = new HashMap<String, Long>();
        var service = new RateLimitService(mockRedis(counters, new HashMap<>()));

        int allowed = 0;
        for (int i = 0; i < 10; i++) {
            if (service.allow(RedisConstant.RATE_COMMENT_PREFIX, 7L)) {
                allowed++;
            }
        }
        assertEquals(3, allowed, "评论一分钟只允许 3 条");
        // 点赞计数不受评论影响
        assertTrue(service.allow(RedisConstant.RATE_LIKE_PREFIX, 7L));
    }

    @Test
    @DisplayName("不同用户互不影响")
    void usersIndependent() {
        var counters = new HashMap<String, Long>();
        var service = new RateLimitService(mockRedis(counters, new HashMap<>()));

        for (int i = 0; i < 25; i++) {
            service.allow(RedisConstant.RATE_COMMENT_PREFIX, 1L);
        }
        assertFalse(service.allow(RedisConstant.RATE_COMMENT_PREFIX, 1L));
        assertTrue(service.allow(RedisConstant.RATE_COMMENT_PREFIX, 2L), "换用户应重新计数");
    }

    @Test
    @DisplayName("去重：同一目标重复操作只算首次")
    void dedupBlocksRepeat() {
        var dedup = new HashMap<String, Boolean>();
        var service = new RateLimitService(mockRedis(new HashMap<>(), dedup));

        assertTrue(service.firstTime(RedisConstant.DEDUP_LIKE_PREFIX, 2, 100L, 5L));
        assertFalse(service.firstTime(RedisConstant.DEDUP_LIKE_PREFIX, 2, 100L, 5L), "重复点赞应被去重挡下");
        // 不同目标互不影响
        assertTrue(service.firstTime(RedisConstant.DEDUP_LIKE_PREFIX, 2, 101L, 5L));
    }

    @Test
    @DisplayName("取消点赞应清掉去重占位，允许重新点赞")
    void clearDedupAllowsRelike() {
        var dedup = new HashMap<String, Boolean>();
        var service = new RateLimitService(mockRedis(new HashMap<>(), dedup));

        service.firstTime(RedisConstant.DEDUP_LIKE_PREFIX, 2, 100L, 5L);
        service.clearDedup(RedisConstant.DEDUP_LIKE_PREFIX, 2, 100L, 5L);
        assertTrue(service.firstTime(RedisConstant.DEDUP_LIKE_PREFIX, 2, 100L, 5L),
                "取消后应能重新点赞");
    }

    @Test
    @DisplayName("角色卡与叙事作品的点赞去重键互相隔离")
    void dedupKeyIsolatedByTargetType() {
        var dedup = new HashMap<String, Boolean>();
        var service = new RateLimitService(mockRedis(new HashMap<>(), dedup));

        assertTrue(service.firstTime(RedisConstant.DEDUP_LIKE_PREFIX, 1, 100L, 5L));
        assertTrue(service.firstTime(RedisConstant.DEDUP_LIKE_PREFIX, 2, 100L, 5L),
                "同样的 id 在不同目标类型下应视为不同操作");
    }
}
