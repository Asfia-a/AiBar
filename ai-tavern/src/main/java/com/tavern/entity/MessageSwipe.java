package com.tavern.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 一条助手消息的某个版本。
 *
 * <p>{@code t_chat_message.content} 永远存「当前展示的那一版」，
 * 这样列表、搜索、导出都不用改；这张表只在用户真的左右滑或多生成一次时才有数据。
 */
@Data
public class MessageSwipe {
    private Long id;
    private Long messageId;
    /** 从 0 开始，0 是最初那条 */
    private Integer idx;
    private String content;
    private Integer tokens;
    private LocalDateTime createdAt;
}
