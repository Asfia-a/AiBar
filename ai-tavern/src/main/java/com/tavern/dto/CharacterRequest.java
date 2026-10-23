package com.tavern.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CharacterRequest {
    @NotBlank(message = "角色名不能为空")
    private String name;
    private String avatar;
    private String description;
    private String personality;
    private String backgroundStory;
    private String exampleDialogue;
    private String greeting;
    private Long categoryId;
    /** 分类名（工坊/导入用），与 categoryId 二选一或互补 */
    private String categoryName;
    private BigDecimal temperature;
    private BigDecimal topP;
    private Integer maxTokens;
    /** 以下为可选采样参数，不填（null）就不下发，交给服务端默认 */
    private Integer topK;
    private BigDecimal frequencyPenalty;
    private BigDecimal presencePenalty;
    private BigDecimal repetitionPenalty;
    /** 停止词，多个用 | 分隔 */
    private String stopWords;
    private List<Long> tagIds;
    /** 标签名列表：不存在则自动创建后再绑定 */
    private List<String> tagNames;
}
