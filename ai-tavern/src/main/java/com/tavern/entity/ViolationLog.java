package com.tavern.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ViolationLog {
    private Long id;
    private Long userId;
    private Long sessionId;
    private Long messageId;
    private String content;
    private String words;
    private LocalDateTime createdAt;

    private String username;
}
