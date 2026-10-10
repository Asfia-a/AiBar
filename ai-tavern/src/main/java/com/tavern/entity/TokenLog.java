package com.tavern.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TokenLog {
    private Long id;
    private Long userId;
    private Long sessionId;
    private Integer tokensUsed;
    private Long balanceBefore;
    private Long balanceAfter;
    private Integer type;
    private LocalDateTime createdAt;
}
