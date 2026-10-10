package com.tavern.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 计费：API Token 与积分换算。
 * 默认 1000 Token = 1 积分（向上取整，至少 1）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "billing")
public class BillingProperties {
    /** 多少 API Token 折算 1 积分 */
    private int tokensPerPoint = 1000;

    public int toPoints(int apiTokens) {
        if (apiTokens <= 0) {
            return 1;
        }
        int rate = Math.max(1, tokensPerPoint);
        return Math.max(1, (apiTokens + rate - 1) / rate);
    }

    public long tokensFromPoints(long points) {
        return points * Math.max(1L, tokensPerPoint);
    }
}
