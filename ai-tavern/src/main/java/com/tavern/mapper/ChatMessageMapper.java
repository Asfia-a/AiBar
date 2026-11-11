package com.tavern.mapper;

import com.tavern.entity.ChatMessage;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ChatMessageMapper {

    // 普通消息不设版本字段，COALESCE 回到表默认（1 / 0）；分支复制时才带值
    @Insert("INSERT INTO t_chat_message(session_id, role, content, reasoning, tokens, swipe_count, swipe_index) "
            + "VALUES(#{sessionId}, #{role}, #{content}, #{reasoning}, #{tokens}, COALESCE(#{swipeCount}, 1), COALESCE(#{swipeIndex}, 0))")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ChatMessage message);

    @Select("SELECT * FROM t_chat_message WHERE session_id=#{sessionId} ORDER BY id ASC")
    List<ChatMessage> listBySession(Long sessionId);

    @Select("SELECT * FROM t_chat_message WHERE session_id=#{sessionId} ORDER BY id DESC LIMIT #{limit}")
    List<ChatMessage> listRecent(@Param("sessionId") Long sessionId, @Param("limit") int limit);

    /**
     * 取 id 落在 (afterId, beforeId) 开区间内的消息，按时间正序。
     * afterId / beforeId 可为 null，表示不设该侧边界。用于摘要「尚未覆盖且即将滚出窗口」的段落。
     */
    @Select("""
            <script>
            SELECT * FROM t_chat_message WHERE session_id=#{sessionId}
            <if test="afterId != null">AND id &gt; #{afterId}</if>
            <if test="beforeId != null">AND id &lt; #{beforeId}</if>
            AND role &lt;&gt; 'system'
            ORDER BY id ASC
            <if test="limit != null">LIMIT #{limit}</if>
            </script>
            """)
    List<ChatMessage> listRange(@Param("sessionId") Long sessionId,
                                @Param("afterId") Long afterId,
                                @Param("beforeId") Long beforeId,
                                @Param("limit") Integer limit);

    @Select("SELECT * FROM t_chat_message WHERE id=#{id}")
    ChatMessage findById(Long id);

    @Delete("DELETE FROM t_chat_message WHERE id=#{id}")
    int deleteById(Long id);

    /**
     * 批量删除：带 session_id 条件，保证只能删自己会话里的消息。
     * 删掉的行不会再被 buildPrompt 读到，等于从模型记忆里清掉。
     */
    @Delete("""
            <script>
            DELETE FROM t_chat_message WHERE session_id=#{sessionId} AND id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    int deleteByIds(@Param("sessionId") Long sessionId, @Param("ids") List<Long> ids);

    /** 会话最后一条消息，删除后用来重算摘要 */
    @Select("SELECT * FROM t_chat_message WHERE session_id=#{sessionId} ORDER BY id DESC LIMIT 1")
    ChatMessage findLast(@Param("sessionId") Long sessionId);

    /** 编辑：只改文本，令牌保持原值（历史计费不追溯） */
    @Update("UPDATE t_chat_message SET content=#{content} WHERE id=#{id}")
    int updateContent(@Param("id") Long id, @Param("content") String content);

    /** 切版本 / 生成新版本后同步「当前展示」与计数 */
    @Update("""
            UPDATE t_chat_message SET content=#{content}, reasoning=#{reasoning}, tokens=#{tokens},
                   swipe_index=#{swipeIndex}, swipe_count=#{swipeCount}
            WHERE id=#{id}
            """)
    int updateActive(@Param("id") Long id, @Param("content") String content,
                     @Param("reasoning") String reasoning,
                     @Param("tokens") int tokens, @Param("swipeIndex") int swipeIndex,
                     @Param("swipeCount") int swipeCount);

    @Select("SELECT COUNT(*) FROM t_chat_message WHERE session_id=#{sessionId} AND id > #{afterId}")
    int countAfter(@Param("sessionId") Long sessionId, @Param("afterId") Long afterId);

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
