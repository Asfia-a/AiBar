package com.tavern.mapper;

import com.tavern.entity.ChatMessage;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ChatMessageMapper {

    @Insert("INSERT INTO t_chat_message(session_id, role, content, tokens) VALUES(#{sessionId}, #{role}, #{content}, #{tokens})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ChatMessage message);

    @Select("SELECT * FROM t_chat_message WHERE session_id=#{sessionId} ORDER BY id ASC")
    List<ChatMessage> listBySession(Long sessionId);

    @Select("SELECT * FROM t_chat_message WHERE session_id=#{sessionId} ORDER BY id DESC LIMIT #{limit}")
    List<ChatMessage> listRecent(@Param("sessionId") Long sessionId, @Param("limit") int limit);

    @Select("SELECT * FROM t_chat_message WHERE id=#{id}")
    ChatMessage findById(Long id);

    @Delete("DELETE FROM t_chat_message WHERE id=#{id}")
    int deleteById(Long id);

    @Select("""
            SELECT m.* FROM t_chat_message m
            INNER JOIN t_chat_session s ON m.session_id=s.id
            WHERE s.user_id=#{userId} AND m.content LIKE CONCAT('%',#{keyword},'%')
            ORDER BY m.id DESC
            """)
    List<ChatMessage> search(@Param("userId") Long userId, @Param("keyword") String keyword);

    @Select("SELECT IFNULL(SUM(tokens),0) FROM t_chat_message WHERE DATE(created_at)=CURDATE()")
    long sumTodayTokens();

    @Select("SELECT IFNULL(SUM(tokens),0) FROM t_chat_message")
    long sumAllTokens();

    @Select("SELECT COUNT(*) FROM t_chat_message WHERE session_id=#{sessionId} AND role<>'system'")
    int countBySession(Long sessionId);
}
