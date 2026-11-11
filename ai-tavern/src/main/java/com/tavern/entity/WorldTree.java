package com.tavern.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WorldTree {
    private Long id;
    private String name;
    private String cover;
    private String description;
    /** 整棵树的 JSON（节点含 children） */
    private String treeJson;
    private Integer nodeCount;
    private Long creatorId;
    /** 0草稿 2已发布 3已下架 */
    private Integer status;
    private Integer viewCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 扩展
    private String creatorName;
    private Object tree;
    private Boolean ownedByMe;
}
