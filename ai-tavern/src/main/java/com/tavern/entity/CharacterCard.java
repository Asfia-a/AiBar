package com.tavern.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CharacterCard {
    private Long id;
    private String name;
    private String avatar;
    private String description;
    private String personality;
    private String backgroundStory;
    private String exampleDialogue;
    private String greeting;
    private Long creatorId;
    private Long categoryId;
    private Integer status;
    private Long sourceId;
    private Integer version;
    private BigDecimal temperature;
    private BigDecimal topP;
    private Integer maxTokens;
    private Integer chatCount;
    private BigDecimal rating;
    private Integer ratingCount;
    private Integer favoriteCount;
    private BigDecimal hotScore;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 扩展字段
    private String creatorName;
    private String categoryName;
    private List<Tag> tags;
    private Boolean favorited;
    private Integer myScore;
    /** 当前登录用户是否为创作者 */
    private Boolean ownedByMe;
}
