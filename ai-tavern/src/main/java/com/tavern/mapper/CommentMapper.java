package com.tavern.mapper;

import com.tavern.entity.Comment;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

@Mapper
public interface CommentMapper {

    @Insert("""
            INSERT INTO t_comment(user_id, target_type, target_id, character_id, content, status)
            VALUES(#{userId}, #{targetType}, #{targetId}, #{characterId}, #{content}, 1)
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Comment comment);

    /** 按统一目标查询，角色卡与叙事作品走同一张表 */
    @Select("""
            SELECT c.*, u.nickname, u.avatar FROM t_comment c
            LEFT JOIN t_user u ON c.user_id=u.id
            WHERE c.target_type=#{targetType} AND c.target_id=#{targetId} AND c.status=1
            ORDER BY c.id DESC
            """)
    List<Comment> listByTarget(@Param("targetType") int targetType, @Param("targetId") Long targetId);

    @Select("SELECT COUNT(*) FROM t_comment WHERE target_type=#{targetType} AND target_id=#{targetId} AND status=1")
    long countByTarget(@Param("targetType") int targetType, @Param("targetId") Long targetId);

    /** 角色卡被删时清掉它的评论；target_type=1 走统一字段 */
    @Delete("DELETE FROM t_comment WHERE target_type=1 AND target_id=#{characterId}")
    int deleteByCharacter(Long characterId);

    @Delete("DELETE FROM t_comment WHERE id=#{id} AND user_id=#{userId}")
    int deleteOwn(@Param("id") Long id, @Param("userId") Long userId);

    /** 后台追溯：某目标下的全部评论（含被隐藏的） */
    @Select("""
            SELECT c.*, u.nickname, u.username FROM t_comment c
            LEFT JOIN t_user u ON c.user_id=u.id
            WHERE c.target_type=#{targetType} AND c.target_id=#{targetId}
            ORDER BY c.id DESC LIMIT 200
            """)
    List<Map<String, Object>> records(@Param("targetType") int targetType, @Param("targetId") Long targetId);

    @Update("UPDATE t_comment SET status=#{status} WHERE id=#{id}")
    int updateStatus(@Param("id") Long id, @Param("status") int status);
}
