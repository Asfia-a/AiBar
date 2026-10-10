package com.tavern.task;

import com.tavern.common.RedisConstant;
import com.tavern.entity.SensitiveWord;
import com.tavern.mapper.ViolationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 定时将敏感词同步到 Redis，供异步内容审核使用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContentAuditTask {

    private final ViolationMapper violationMapper;
    private final StringRedisTemplate stringRedisTemplate;

    /** 每 10 分钟刷新敏感词缓存 */
    @Scheduled(cron = "0 */10 * * * ?")
    public void refreshSensitiveWords() {
        try {
            List<SensitiveWord> words = violationMapper.listWords();
            stringRedisTemplate.delete(RedisConstant.SENSITIVE_WORDS);
            if (words != null && !words.isEmpty()) {
                String[] arr = words.stream()
                        .map(SensitiveWord::getWord)
                        .filter(w -> w != null && !w.isBlank())
                        .toArray(String[]::new);
                if (arr.length > 0) {
                    stringRedisTemplate.opsForSet().add(RedisConstant.SENSITIVE_WORDS, arr);
                    stringRedisTemplate.expire(RedisConstant.SENSITIVE_WORDS, 1, TimeUnit.DAYS);
                }
            }
            log.info("敏感词缓存已刷新, count={}", words == null ? 0 : words.size());
        } catch (Exception e) {
            log.warn("敏感词缓存刷新失败: {}", e.getMessage());
        }
    }
}
