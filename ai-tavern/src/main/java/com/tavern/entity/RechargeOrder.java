package com.tavern.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class RechargeOrder {
    private Long id;
    private String orderNo;
    private Long userId;
    private BigDecimal amount;
    /** 对应库字段 token_count */
    private Long tokens;
    private String payType;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime paidAt;
}
