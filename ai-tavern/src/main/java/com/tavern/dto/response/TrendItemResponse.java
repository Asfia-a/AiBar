package com.tavern.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TrendItemResponse {
    private String date;
    private long count;
}
