package com.tavern.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Comment {
    private Long id;
    private Long userId;
    /** 1角色卡 2叙事作品 */
    private Integer targetType;
    private Long targetId;
    /** 冗余保留：targetType=1 时等于 targetId，方便兼容角色卡侧的既有查询 */
    private Long characterId;
    private String content;
    private Integer status;
    private LocalDateTime createdAt;

    private String nickname;
    private String avatar;
    private String username;
}
