package com.tavern.mapper;

import org.apache.ibatis.annotations.*;

@Mapper
public interface MessageCollectMapper {

    @Insert("INSERT IGNORE INTO t_message_collect(user_id, message_id) VALUES(#{userId}, #{messageId})")
    int insert(@Param("userId") Long userId, @Param("messageId") Long messageId);

    /** 消息被删除时，对应的收藏记录一起清掉，否则消息页会出现打不开的收藏项 */
    @Delete("""
            <script>
            DELETE FROM t_message_collect WHERE message_id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    int deleteByMessageIds(@Param("ids") java.util.List<Long> ids);
}
