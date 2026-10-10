package com.tavern.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ModelConfig {
    private Long id;
    private String provider;
    private String apiKey;
    private String baseUrl;
    private String modelName;
    private BigDecimal temperature;
    private BigDecimal topP;
    private Integer maxTokens;
    private Integer status;
    private LocalDateTime updatedAt;

    /** 逻辑字段：上下文窗口，默认 20（库表无此列时使用） */
    private Integer contextWindowSize = 20;
}
