package com.tavern.mapper;

import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

@Mapper
public interface InteractionMapper {

    /** 点赞。唯一索引 + INSERT IGNORE 保证重复点赞不会插出第二条 */
    @Insert("INSERT IGNORE INTO t_like(user_id, target_type, target_id) VALUES(#{userId}, #{targetType}, #{targetId})")
    int like(@Param("userId") Long userId, @Param("targetType") int targetType, @Param("targetId") Long targetId);

    @Delete("DELETE FROM t_like WHERE user_id=#{userId} AND target_type=#{targetType} AND target_id=#{targetId}")
    int unlike(@Param("userId") Long userId, @Param("targetType") int targetType, @Param("targetId") Long targetId);

    @Insert("INSERT IGNORE INTO t_collect(user_id, target_type, target_id) VALUES(#{userId}, #{targetType}, #{targetId})")
    int collect(@Param("userId") Long userId, @Param("targetType") int targetType, @Param("targetId") Long targetId);

    @Delete("DELETE FROM t_collect WHERE user_id=#{userId} AND target_type=#{targetType} AND target_id=#{targetId}")
    int uncollect(@Param("userId") Long userId, @Param("targetType") int targetType, @Param("targetId") Long targetId);

    @Select("SELECT COUNT(*) FROM t_like WHERE target_type=#{targetType} AND target_id=#{targetId}")
    long likeCount(@Param("targetType") int targetType, @Param("targetId") Long targetId);

    @Select("SELECT COUNT(*) FROM t_collect WHERE target_type=#{targetType} AND target_id=#{targetId}")
    long collectCount(@Param("targetType") int targetType, @Param("targetId") Long targetId);

    @Select("SELECT COUNT(*) FROM t_like WHERE user_id=#{userId} AND target_type=#{targetType} AND target_id=#{targetId}")
    int likedBy(@Param("userId") Long userId, @Param("targetType") int targetType, @Param("targetId") Long targetId);

    @Select("SELECT COUNT(*) FROM t_collect WHERE user_id=#{userId} AND target_type=#{targetType} AND target_id=#{targetId}")
    int collectedBy(@Param("userId") Long userId, @Param("targetType") int targetType, @Param("targetId") Long targetId);

    /**
     * 批量取一组目标的点赞/收藏计数，避免列表页 N+1 查询。
     * 返回 key 形如 {@code 1:12}（targetType:targetId）。
     */
    @Select("""
            <script>
            SELECT CONCAT(target_type, ':', target_id) AS target_key, COUNT(*) AS cnt
            FROM t_like WHERE target_type = #{targetType} AND target_id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            GROUP BY target_type, target_id
            </script>
            """)
    List<Map<String, Object>> likeCounts(@Param("targetType") int targetType, @Param("ids") List<Long> ids);

    @Select("""
            <script>
            SELECT CONCAT(target_type, ':', target_id) AS target_key, COUNT(*) AS cnt
            FROM t_collect WHERE target_type = #{targetType} AND target_id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            GROUP BY target_type, target_id
            </script>
            """)
    List<Map<String, Object>> collectCounts(@Param("targetType") int targetType, @Param("ids") List<Long> ids);

    /** 当前用户在这批目标里已点赞的 id，用于回显按钮状态 */
    @Select("""
            <script>
            SELECT target_id FROM t_like WHERE user_id=#{userId} AND target_type=#{targetType}
            AND target_id IN <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<Long> likedIds(@Param("userId") Long userId, @Param("targetType") int targetType,
                        @Param("ids") List<Long> ids);

    @Select("""
            <script>
            SELECT target_id FROM t_collect WHERE user_id=#{userId} AND target_type=#{targetType}
            AND target_id IN <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<Long> collectedIds(@Param("userId") Long userId, @Param("targetType") int targetType,
                            @Param("ids") List<Long> ids);

    /** 后台追溯：按目标查全部点赞记录 */
    @Select("""
            SELECT l.*, u.nickname, u.username FROM t_like l
            LEFT JOIN t_user u ON l.user_id = u.id
            WHERE l.target_type=#{targetType} AND l.target_id=#{targetId}
            ORDER BY l.id DESC LIMIT 200
            """)
    List<Map<String, Object>> likeRecords(@Param("targetType") int targetType, @Param("targetId") Long targetId);

    /** 我收藏过的作品 id，用于"我的收藏"页 */
    @Select("""
            SELECT target_id FROM t_collect WHERE user_id=#{userId} AND target_type=#{targetType}
            ORDER BY id DESC
            """)
    List<Long> collectedWorkIds(@Param("userId") Long userId, @Param("targetType") int targetType);
}
