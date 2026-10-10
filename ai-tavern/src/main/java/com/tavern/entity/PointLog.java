package com.tavern.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PointLog {
    private Long id;
    private Long userId;
    private Integer points;
    private String type;
    private String remark;
    private LocalDateTime createdAt;
}
