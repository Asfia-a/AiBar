package com.tavern.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SessionCreateRequest {
    @NotNull
    private Long characterId;
    private String title;
}
