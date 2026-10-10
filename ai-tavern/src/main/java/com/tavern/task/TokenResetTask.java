package com.tavern.task;

import com.tavern.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TokenResetTask {

    private final UserMapper userMapper;

    /** 每日 0 点按会员等级重置 daily_free_token */
    @Scheduled(cron = "0 0 0 * * ?")
    public void resetDailyFreeToken() {
        userMapper.resetDailyFreeByLevel(0, 2000);
        userMapper.resetDailyFreeByLevel(1, 5000);
        userMapper.resetDailyFreeByLevel(2, 10000);
        userMapper.resetDailyFreeByLevel(3, 20000);
        log.info("已重置各会员等级每日免费 Token");
    }
}
