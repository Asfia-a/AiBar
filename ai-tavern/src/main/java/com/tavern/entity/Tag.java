package com.tavern.entity;

import lombok.Data;

@Data
public class Tag {
    private Long id;
    private String name;
    private String color;
    private Integer usageCount;
}
