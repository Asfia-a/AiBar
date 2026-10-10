package com.tavern.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserProfileResponse {
    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private String email;
    private String phone;
    private Integer role;
    private Integer vipLevel;
    private LocalDateTime vipExpireAt;
    private Long tokenBalance;
    private Long dailyFreeToken;
    private Integer points;
    private Integer status;
}
