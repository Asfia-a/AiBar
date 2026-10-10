package com.tavern.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class RechargeRequest {
    @NotNull
    private BigDecimal amount;
}
