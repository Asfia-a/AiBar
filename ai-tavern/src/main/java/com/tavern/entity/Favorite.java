package com.tavern.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Favorite {
    private Long id;
    private Long userId;
    private Long characterId;
    private LocalDateTime createdAt;
}
