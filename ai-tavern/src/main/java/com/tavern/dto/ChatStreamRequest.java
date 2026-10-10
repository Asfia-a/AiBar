package com.tavern.dto;

import lombok.Data;

@Data
public class ChatStreamRequest {
    private Long sessionId;
    private Long characterId;
    private String message;
}
