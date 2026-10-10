package com.tavern.entity;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class User {
    private Long id;
    private String username;
    private String password;
    private String avatar;
    private String nickname;
    private String email;
    private String phone;
    private Long tokenBalance;
    private Integer memberLevel;
    private Long dailyFreeToken;
    private LocalDate lastCheckIn;
    private Integer consecutiveDays;
    private Integer points;
    private Integer role;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
