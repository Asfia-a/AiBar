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
    /** 作者注释：只在本次会话生效的一段强指令，插在历史对话里 */
    private String authorNote;
    /** 插入深度：0=历史末尾，2=倒数第 2 条之前 */
    private Integer anDepth;
    /** 插入频率：每 N 条用户消息插一次，0=不插 */
    private Integer anFrequency;
    /** 本会话选用的模型名；空则回落配置默认值 */
    private String modelName;
    /** 用户人设称呼，优先于账号昵称作为 {{user}} */
    private String personaName;
    /** 用户人设描述，注入提示词「关于用户」 */
    private String personaDesc;
    /** 被窗口裁掉的早期剧情摘要 */
    private String memorySummary;
    /** 摘要已覆盖到的消息 id（含） */
    private Long memoryUntilId;
    /** 1=自动摘要，0=关闭 */
    private Integer memoryEnabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private String characterName;
    private String characterAvatar;
}
