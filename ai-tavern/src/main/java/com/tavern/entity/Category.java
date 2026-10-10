package com.tavern.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Category {
    private Long id;
    private String name;
    /** 对应库列 sort */
    private Integer sort;
    private LocalDateTime createdAt;
}
