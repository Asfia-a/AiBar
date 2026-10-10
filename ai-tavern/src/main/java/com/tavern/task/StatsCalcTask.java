package com.tavern.task;

import com.tavern.common.RedisConstant;
import com.tavern.entity.CharacterCard;
import com.tavern.mapper.CharacterMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class StatsCalcTask {

    private final CharacterMapper characterMapper;
    private final StringRedisTemplate stringRedisTemplate;

    /** 每小时刷新热度并写入 Redis ZSet */
    @Scheduled(cron = "0 5 * * * ?")
    public void calcHotScore() {
        try {
            characterMapper.recalcAllHotScore();
            List<CharacterCard> list = characterMapper.listHotScores();
            stringRedisTemplate.delete(RedisConstant.CHARACTER_HOT);
            for (CharacterCard c : list) {
                if (c.getHotScore() != null) {
                    stringRedisTemplate.opsForZSet().add(
                            RedisConstant.CHARACTER_HOT,
                            String.valueOf(c.getId()),
                            c.getHotScore().doubleValue()
                    );
                }
            }
            log.info("热度分刷新完成, count={}", list.size());
        } catch (Exception e) {
            log.warn("热度分刷新失败: {}", e.getMessage());
        }
    }
}
