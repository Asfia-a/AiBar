package com.tavern.dto.response;

import lombok.Data;

@Data
public class DashboardStatsResponse {
    private long userCount;
    private long characterCount;
    private long sessionCount;
    private long messageCount;
}
