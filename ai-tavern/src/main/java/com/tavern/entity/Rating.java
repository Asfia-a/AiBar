package com.tavern.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Rating {
    private Long id;
    private Long userId;
    private Long characterId;
    private Integer score;
    private LocalDateTime createdAt;
}
