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
    /** top_k 采样：非 OpenAI 标准参数，只有自建推理服务（Ollama / vLLM 等）认，云端网关可能直接报错 */
    private Integer topK;
    /** -2~2，按已出现词频惩罚，抑制复读 */
    private BigDecimal frequencyPenalty;
    /** -2~2，鼓励引入新话题 */
    private BigDecimal presencePenalty;
    /** 0.8~1.5，非 OpenAI 标准，1 等于不惩罚 */
    private BigDecimal repetitionPenalty;
    /** 停止词，多个用 | 分隔 */
    private String stopWords;
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
