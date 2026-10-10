package com.tavern.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AuditRequest {
    @NotNull
    private Integer status;
    private String remark;
}
