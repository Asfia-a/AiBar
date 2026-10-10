package com.tavern.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatSession {
    private Long id;
    private Long userId;
    private Long characterId;
    private String title;
    private String lastMessage;
    private String lastRole;
    private Integer messageCount;
    private Long totalTokens;
    /** 会话创建时冻结的角色人设 JSON，后续改卡不影响本会话 */
    private String promptSnapshot;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private String characterName;
    private String characterAvatar;
}
