package com.tavern.common;

public final class RedisConstant {
    private RedisConstant() {}

    /** 热度排行 ZSet（member=characterId, score=hotScore），仅供统计任务读写 */
    public static final String CHARACTER_HOT = "character:hot:zset";
    /** 旧版共用键，启动/失效时顺带删掉，避免和列表缓存撞车 */
    public static final String CHARACTER_HOT_LEGACY = "character:hot";
    public static final String SENSITIVE_WORDS = "sensitive:words";
    public static final String DAU_PREFIX = "stats:dau:";

    /** 敏感词库版本号，词库增删改后自增，审核侧据此判断是否重建自动机 */
    public static final String SENSITIVE_VERSION = "sensitive:version";

    /** 缓存埋点流水（List，保留最近 500 条），供管理端画命中对比 */
    public static final String CACHE_TRACE = "stats:cache:trace";

    /** 上下文裁剪采样（List，保留最近 300 条，3 天过期） */
    public static final String METRIC_CONTEXT = "metrics:context";
    /** MCP 工具调用时间线（List，保留最近 200 条，3 天过期） */
    public static final String METRIC_MCP = "metrics:mcp";

    /** 热门角色列表 JSON 缓存（与 ZSet 必须分键，否则类型互相覆盖） */
    public static final String HOT_CHARACTERS = "character:hot:list";
    /** 标签列表缓存 */
    public static final String TAG_LIST = "tag:list";
    /** 模型配置缓存 */
    public static final String MODEL_CONFIG = "model:config";

    /** 社区：作品点赞数（ZSet，member 为 workId，score 为计数） */
    public static final String WORK_LIKE_ZSET = "community:work:likes";
    /** 社区：作品收藏数 */
    public static final String WORK_COLLECT_ZSET = "community:work:collects";

    /** 频控滑动窗口前缀，格式：rate:comment:{userId} / rate:like:{userId} */
    public static final String RATE_COMMENT_PREFIX = "rate:comment:";
    public static final String RATE_LIKE_PREFIX = "rate:like:";

    /** 去重占位，格式：dedup:like:{targetType}:{targetId}:{userId} */
    public static final String DEDUP_LIKE_PREFIX = "dedup:like:";
    public static final String DEDUP_COLLECT_PREFIX = "dedup:collect:";
    public static final String DEDUP_COMMENT_PREFIX = "dedup:comment:";
}
