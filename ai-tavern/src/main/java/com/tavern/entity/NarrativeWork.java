package com.tavern.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NarrativeWork {
    private Long id;
    private Long userId;
    /** 来源会话，保留可追溯；作品保存后与源会话解绑 */
    private Long sessionId;
    private Long characterId;
    private String title;
    private String summary;
    private String content;
    /** novel小说 / script剧本 / outline大纲 */
    private String formType;
    /** literary literary 文学性口语 colloquial 口语 script 剧本感 poetic 诗意 */
    private String style;
    /** short短篇 medium中篇 long长篇 */
    private String length;
    private String extraInstruction;
    private Integer wordCount;
    private Integer sourceMessageCount;
    private Integer tokens;
    /** 0草稿 1已发布 */
    private Integer status;
    /** 0待审核 1通过 2未通过；仅发布后有效 */
    private Integer auditStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private String characterName;
    private String characterAvatar;
    private String nickname;
    private String avatar;

    /** 以下为社区互动字段，列表/详情接口填充，不落库 */
    private Integer likeCount;
    private Integer collectCount;
    private Integer commentCount;
    private Boolean liked;
    private Boolean collected;
}
