package com.tavern.service;

import com.tavern.common.RedisConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 社区操作的频控与去重。
 *
 * <p>频控用 Redis 计数器的固定窗口实现：{@code INCR} 之后首次设置过期时间，
 * 窗口内累计次数超过阈值即拒绝。窗口长度与阈值都按操作类型区分开，
 * 点赞比评论宽松，因为点赞是轻量高频动作。
 *
 * <p>去重分两层：Redis 先用 {@code SET NX} 占位挡住连点（毫秒级返回），
 * 数据库唯一索引兜底（{@code INSERT IGNORE}）。两层都不可省——Redis 可能过期或被清空，
 * 只靠 Redis 会漏；只靠数据库则每次都要等一次 INSERT 才知道是不是重复。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final StringRedisTemplate redis;

    /** 评论窗口内最多 3 条 */
    private static final int COMMENT_LIMIT = 3;
    private static final Duration COMMENT_WINDOW = Duration.ofMinutes(1);
    /** 点赞窗口内最多 20 次 */
    private static final int LIKE_LIMIT = 20;
    private static final Duration LIKE_WINDOW = Duration.ofMinutes(1);
    /** 去重占位保留一天，跨天允许重新评价 */
    private static final Duration DEDUP_TTL = Duration.ofDays(1);

    /**
     * 频控检查。通过返回 true，超限抛业务异常。
     *
     * @param keyPrefix RedisConstant 中的频控前缀
     */
    public boolean allow(String keyPrefix, Long userId) {
        int limit = keyPrefix.equals(RedisConstant.RATE_COMMENT_PREFIX) ? COMMENT_LIMIT : LIKE_LIMIT;
        Duration window = keyPrefix.equals(RedisConstant.RATE_COMMENT_PREFIX) ? COMMENT_WINDOW : LIKE_WINDOW;
        String key = keyPrefix + userId;

        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            // 首次计数时开窗；用 setIfAbsent 避免与并发请求竞争导致重复设 TTL
            redis.opsForValue().setIfAbsent(key, "1", window);
        }
        if (count != null && count > limit) {
            log.debug("频控拦截: key={} count={} limit={}", key, count, limit);
            return false;
        }
        return true;
    }

    /**
     * 去重占位。返回 true 表示是首次操作，可以继续；false 表示已操作过。
     */
    public boolean firstTime(String keyPrefix, int targetType, Long targetId, Long userId) {
        String key = keyPrefix + targetType + ":" + targetId + ":" + userId;
        Boolean ok = redis.opsForValue().setIfAbsent(key, "1", DEDUP_TTL);
        return Boolean.TRUE.equals(ok);
    }

    /** 取消去重占位，用于取消点赞/取消收藏 */
    public void clearDedup(String keyPrefix, int targetType, Long targetId, Long userId) {
        redis.delete(keyPrefix + targetType + ":" + targetId + ":" + userId);
    }

    /** 查询剩余窗口秒数，给前端提示用；key 不存在返回 0 */
    public long remainSeconds(String keyPrefix, Long userId) {
        Long ttl = redis.getExpire(keyPrefix + userId, TimeUnit.SECONDS);
        return ttl == null || ttl < 0 ? 0 : ttl;
    }
}
