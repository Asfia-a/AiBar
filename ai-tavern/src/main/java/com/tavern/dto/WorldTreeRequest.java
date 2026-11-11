package com.tavern.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WorldTreeRequest {
    @NotBlank(message = "请填写世界名称")
    private String name;
    private String cover;
    private String description;
    /** 世界树节点数组（含 children） */
    @NotNull(message = "世界树内容不能为空")
    private Object tree;
}
