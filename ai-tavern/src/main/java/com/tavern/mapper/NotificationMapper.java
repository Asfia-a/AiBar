package com.tavern.mapper;

import com.tavern.entity.Notification;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface NotificationMapper {

    @Insert("INSERT INTO t_notification(user_id, type, title, content, is_read) VALUES(#{userId}, #{type}, #{title}, #{content}, 0)")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Notification n);

    @Select("SELECT * FROM t_notification WHERE user_id=#{userId} ORDER BY id DESC")
    List<Notification> listByUser(Long userId);

    @Select("SELECT COUNT(*) FROM t_notification WHERE user_id=#{userId} AND is_read=0")
    int countUnread(Long userId);

    @Update("UPDATE t_notification SET is_read=1 WHERE id=#{id} AND user_id=#{userId}")
    int markRead(@Param("id") Long id, @Param("userId") Long userId);

    @Update("UPDATE t_notification SET is_read=1 WHERE user_id=#{userId}")
    int markAllRead(Long userId);
}
