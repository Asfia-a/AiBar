package com.tavern.entity;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class CheckIn {
    private Long id;
    private Long userId;
    private LocalDate checkDate;
    private Integer points;
    private Integer consecutive;
    private LocalDateTime createdAt;
}
