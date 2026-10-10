package com.tavern.mapper;

import com.tavern.entity.NarrativeWork;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

@Mapper
public interface NarrativeWorkMapper {

    @Insert("""
            INSERT INTO t_narrative_work(user_id, session_id, character_id, title, summary, content, form_type, style,
            length, extra_instruction, word_count, source_message_count, tokens, status, audit_status)
            VALUES(#{userId}, #{sessionId}, #{characterId}, #{title}, #{summary}, #{content}, #{formType}, #{style},
            #{length}, #{extraInstruction}, #{wordCount}, #{sourceMessageCount}, #{tokens}, #{status}, #{auditStatus})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(NarrativeWork work);

    @Select("SELECT * FROM t_narrative_work WHERE id=#{id}")
    NarrativeWork findById(Long id);

    @Update("""
            UPDATE t_narrative_work SET title=#{title}, content=#{content}, summary=#{summary},
            word_count=#{wordCount} WHERE id=#{id} AND user_id=#{userId}
            """)
    int updateContent(NarrativeWork work);

    @Update("UPDATE t_narrative_work SET status=#{status}, audit_status=#{auditStatus} WHERE id=#{id} AND user_id=#{userId}")
    int updatePublish(@Param("id") Long id, @Param("userId") Long userId,
                      @Param("status") int status, @Param("auditStatus") int auditStatus);

    @Delete("DELETE FROM t_narrative_work WHERE id=#{id} AND user_id=#{userId}")
    int deleteByUser(@Param("id") Long id, @Param("userId") Long userId);

    @Select("""
            SELECT w.*, u.nickname, u.avatar, c.name AS character_name, c.avatar_url AS character_avatar
            FROM t_narrative_work w
            LEFT JOIN t_user u ON w.user_id = u.id
            LEFT JOIN t_character c ON w.character_id = c.id
            WHERE w.user_id=#{userId}
            ORDER BY w.id DESC
            """)
    List<NarrativeWork> listByUser(@Param("userId") Long userId);

    /** 社区广场：已发布且审核通过的公开作品 */
    @Select("""
            SELECT w.*, u.nickname, u.avatar, c.name AS character_name, c.avatar_url AS character_avatar
            FROM t_narrative_work w
            LEFT JOIN t_user u ON w.user_id = u.id
            LEFT JOIN t_character c ON w.character_id = c.id
            WHERE w.status=1 AND w.audit_status=1
            ORDER BY w.id DESC
            """)
    List<NarrativeWork> listPublished();

    @Select("SELECT COUNT(*) FROM t_narrative_work WHERE user_id=#{userId}")
    long countByUser(Long userId);

    @Select("SELECT COUNT(*) FROM t_narrative_work WHERE status=1 AND audit_status=1")
    long countPublished();

    /** 热榜回表：按给定 id 集合取作品，排序由 Service 层按 ZSet 分数重排 */
    @Select("""
            <script>
            SELECT w.*, u.nickname, u.avatar, c.name AS character_name, c.avatar_url AS character_avatar
            FROM t_narrative_work w
            LEFT JOIN t_user u ON w.user_id = u.id
            LEFT JOIN t_character c ON w.character_id = c.id
            WHERE w.id IN <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            AND w.status=1 AND w.audit_status=1
            </script>
            """)
    List<NarrativeWork> findByIds(@Param("ids") List<Long> ids);

    @Select("SELECT IFNULL(SUM(tokens),0) FROM t_narrative_work WHERE DATE(created_at)=CURDATE()")
    long sumTodayTokens();

    @Select("SELECT IFNULL(SUM(word_count),0) FROM t_narrative_work")
    long sumAllWords();

    @Select("""
            SELECT DATE(created_at) AS day, COUNT(*) AS cnt FROM t_narrative_work
            WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY)
            GROUP BY DATE(created_at) ORDER BY day
            """)
    List<Map<String, Object>> workTrend(@Param("days") int days);
}
