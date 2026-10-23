package com.tavern.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 世界书条目提交体。
 *
 * <p>前端只传「人写的字段」，position/depth/order/probability 这类需要在服务端兜底，
 * 不填就走默认值（after_char / 4 / 100 / 100），避免脏数据让拼装阶段抛异常。
 */
@Data
public class WorldInfoRequest {

    @NotBlank(message = "备注名不能为空")
    @Size(max = 100, message = "备注名最多 100 字")
    private String title;

    /** 英文逗号分隔，/.../ 形式按正则 */
    @Size(max = 500, message = "触发词最多 500 字")
    private String keys;

    @NotBlank(message = "插入内容不能为空")
    private String content;

    private Integer orderNo;
    /** before_char / after_char / in_chat */
    private String position;
    private Integer depth;
    private Integer probability;
    /** 1=无条件插入 */
    private Boolean constant;
    private Boolean enabled;
}
