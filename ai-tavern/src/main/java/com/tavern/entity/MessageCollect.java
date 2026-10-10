package com.tavern.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MessageCollect {
    private Long id;
    private Long userId;
    private Long messageId;
    private LocalDateTime createdAt;
}
