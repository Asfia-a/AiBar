package com.tavern.mapper;

import com.tavern.entity.MessageSwipe;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/** 助手消息的多版本存储。 */
@Mapper
public interface MessageSwipeMapper {

    @Insert("INSERT INTO t_message_swipe(message_id, idx, content, tokens) VALUES(#{messageId}, #{idx}, #{content}, #{tokens})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(MessageSwipe swipe);

    @Select("SELECT * FROM t_message_swipe WHERE message_id=#{messageId} ORDER BY idx ASC")
    List<MessageSwipe> findByMessage(@Param("messageId") Long messageId);

    @Select("SELECT * FROM t_message_swipe WHERE message_id=#{messageId} AND idx=#{idx}")
    MessageSwipe findByMessageAndIdx(@Param("messageId") Long messageId, @Param("idx") int idx);

    /** 编辑某个版本：内容改了令牌不变，历史计费不追溯 */
    @Update("UPDATE t_message_swipe SET content=#{content} WHERE message_id=#{messageId} AND idx=#{idx}")
    int updateContent(@Param("messageId") Long messageId, @Param("idx") int idx, @Param("content") String content);
}
