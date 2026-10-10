package com.tavern.mapper;

import org.apache.ibatis.annotations.*;

@Mapper
public interface MessageCollectMapper {

    @Insert("INSERT IGNORE INTO t_message_collect(user_id, message_id) VALUES(#{userId}, #{messageId})")
    int insert(@Param("userId") Long userId, @Param("messageId") Long messageId);
}
