package com.tavern.common;

/** 社区互动的目标类型。角色卡与叙事作品共用评论/点赞/收藏三套表，靠它区分。 */
public final class TargetType {
    private TargetType() {}

    /** 角色卡 */
    public static final int CHARACTER = 1;
    /** 叙事作品 */
    public static final int NARRATIVE = 2;

    public static String label(int type) {
        return type == NARRATIVE ? "叙事作品" : "角色卡";
    }
}
