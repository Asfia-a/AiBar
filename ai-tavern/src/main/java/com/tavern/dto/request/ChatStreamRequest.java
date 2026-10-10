package com.tavern.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ChatStreamRequest {
    @NotNull
    private Long sessionId;
    @NotBlank
    private String content;
}
