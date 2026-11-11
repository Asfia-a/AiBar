package com.tavern.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatMessage {
    private Long id;
    private Long sessionId;
    private String role;
    private String content;
    /** 思考过程（DeepSeek reasoning_content）；空表示无 */
    private String reasoning;
    private Integer tokens;
    /** 这条回复共有几个版本（含当前），=1 时前端不显示左右箭头 */
    private Integer swipeCount;
    /** 当前展示的版本序号，从 0 开始 */
    private Integer swipeIndex;
    private LocalDateTime createdAt;
}
