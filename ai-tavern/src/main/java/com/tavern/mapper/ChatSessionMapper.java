package com.tavern.mapper;

import com.tavern.entity.ChatSession;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

@Mapper
public interface ChatSessionMapper {

    @Select("SELECT s.*, c.name AS character_name, c.avatar AS character_avatar FROM t_chat_session s " +
            "LEFT JOIN t_character c ON s.character_id=c.id WHERE s.id=#{id}")
    ChatSession findById(Long id);

    @Insert("""
            INSERT INTO t_chat_session(user_id, character_id, title, last_message, last_role, message_count, total_tokens, prompt_snapshot, model_name)
            VALUES(#{userId}, #{characterId}, #{title}, #{lastMessage}, #{lastRole}, #{messageCount}, #{totalTokens}, #{promptSnapshot}, #{modelName})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ChatSession session);

    @Update("""
            UPDATE t_chat_session SET last_message=#{lastMessage}, last_role=#{lastRole},
            message_count=#{messageCount}, total_tokens=#{totalTokens}, updated_at=NOW() WHERE id=#{id}
            """)
    int update(ChatSession session);

    @Update("UPDATE t_chat_session SET prompt_snapshot=#{promptSnapshot} WHERE id=#{id} AND (prompt_snapshot IS NULL OR prompt_snapshot='')")
    int updatePromptSnapshot(@Param("id") Long id, @Param("promptSnapshot") String promptSnapshot);

    /** 作者注释是会话级设置，改角色卡不影响它 */
    @Update("""
            UPDATE t_chat_session SET author_note=#{note}, an_depth=#{depth}, an_frequency=#{frequency},
            updated_at=NOW() WHERE id=#{id} AND user_id=#{userId}
            """)
    int updateAuthorNote(@Param("id") Long id, @Param("userId") Long userId,
                         @Param("note") String note, @Param("depth") int depth,
                         @Param("frequency") int frequency);

    /** 聊天页切换模型：只改模型名，密钥仍走配置文件 */
    @Update("""
            UPDATE t_chat_session SET model_name=#{modelName}, updated_at=NOW()
            WHERE id=#{id} AND user_id=#{userId}
            """)
    int updateModelName(@Param("id") Long id, @Param("userId") Long userId,
                        @Param("modelName") String modelName);

    @Update("""
            UPDATE t_chat_session SET persona_name=#{personaName}, persona_desc=#{personaDesc},
            updated_at=NOW() WHERE id=#{id} AND user_id=#{userId}
            """)
    int updatePersona(@Param("id") Long id, @Param("userId") Long userId,
                      @Param("personaName") String personaName,
                      @Param("personaDesc") String personaDesc);

    @Update("""
            UPDATE t_chat_session SET memory_summary=#{summary}, memory_until_id=#{untilId},
            memory_enabled=#{enabled}, updated_at=NOW()
            WHERE id=#{id} AND user_id=#{userId}
            """)
    int updateMemory(@Param("id") Long id, @Param("userId") Long userId,
                     @Param("summary") String summary, @Param("untilId") Long untilId,
                     @Param("enabled") int enabled);

    @Select("SELECT s.*, c.name AS character_name, c.avatar AS character_avatar FROM t_chat_session s " +
            "LEFT JOIN t_character c ON s.character_id=c.id WHERE s.user_id=#{userId} ORDER BY s.updated_at DESC")
    List<ChatSession> listByUser(Long userId);

    @Select("SELECT s.*, c.name AS character_name, c.avatar AS character_avatar FROM t_chat_session s " +
            "LEFT JOIN t_character c ON s.character_id=c.id " +
            "WHERE s.user_id=#{userId} AND s.character_id=#{characterId} " +
            "ORDER BY s.updated_at DESC LIMIT 1")
    ChatSession findLatestByUserAndCharacter(@Param("userId") Long userId, @Param("characterId") Long characterId);

    @Select("SELECT id, character_id FROM t_chat_session WHERE prompt_snapshot IS NULL OR prompt_snapshot=''")
    List<ChatSession> listMissingSnapshots();

    @Delete("DELETE FROM t_chat_session WHERE id=#{id} AND user_id=#{userId}")
    int delete(@Param("id") Long id, @Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM t_chat_session")
    long countAll();

    @Select("SELECT COUNT(*) FROM t_chat_session WHERE DATE(created_at)=CURDATE()")
    long countToday();

    @Select("""
            SELECT DATE(created_at) AS day, COUNT(*) AS cnt FROM t_chat_session
            WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY)
            GROUP BY DATE(created_at) ORDER BY day
            """)
    List<Map<String, Object>> sessionTrend(@Param("days") int days);
}
