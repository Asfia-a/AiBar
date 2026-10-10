package com.tavern.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CharacterVO {
    private Long id;
    private Long userId;
    private String name;
    private String avatar;
    private String cover;
    private String description;
    private String personality;
    private String background;
    private String exampleDialogue;
    private String greeting;
    private Long categoryId;
    private Integer status;
    private Integer isPublic;
    private Double hotScore;
    private Integer chatCount;
    private Integer favoriteCount;
    private BigDecimal ratingAvg;
    private Integer ratingCount;
    private List<String> tags;
    private Boolean favorited;
    private LocalDateTime createdAt;
}
