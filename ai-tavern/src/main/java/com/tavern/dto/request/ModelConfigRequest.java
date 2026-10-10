package com.tavern.dto.request;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ModelConfigRequest {
    private String provider;
    private String apiKey;
    private String baseUrl;
    private String modelName;
    private Integer contextWindowSize;
    private BigDecimal temperatureDefault;
    private Integer enabled;
}
