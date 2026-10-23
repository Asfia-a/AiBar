package com.tavern.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 世界书条目（对标 SillyTavern 的 World Info / Lorebook）。
 *
 * <p>核心思想：设定不需要全部塞进 prompt，只有当对话里出现了某个关键词时，
 * 才把对应条目插进去。既省 token，又不会让模型被一堆无关设定带偏。
 */
@Data
public class WorldInfoEntry {
    private Long id;
    /** 所属角色，NULL 表示全局条目 */
    private Long characterId;
    private Long userId;
    /** 备注名，只给人看，不进 prompt */
    private String title;
    /** 触发关键词，英文逗号分隔；/.../ 形式按正则处理 */
    private String keys;
    /** 命中后插入正文的内容 */
    private String content;
    /** 插入顺序，数值越大越靠近上下文末尾（影响越强） */
    private Integer orderNo;
    /** before_char / after_char / in_chat */
    private String position;
    /** position=in_chat 时，插到倒数第 depth 条消息之前 */
    private Integer depth;
    /** 触发概率 0-100，用来做随机彩蛋 */
    private Integer probability;
    /** 1=无条件插入 */
    private Integer constantOn;
    /** 1=启用 */
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public boolean isConstant() {
        return constantOn != null && constantOn == 1;
    }

    public boolean isEnabled() {
        return enabled == null || enabled == 1;
    }
}
