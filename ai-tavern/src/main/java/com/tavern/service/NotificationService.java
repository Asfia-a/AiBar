package com.tavern.service;

import com.github.pagehelper.PageHelper;
import com.tavern.common.PageResult;
import com.tavern.entity.Notification;
import com.tavern.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationMapper notificationMapper;

    public PageResult<Notification> list(Long userId, int page, int size) {
        PageHelper.startPage(page, size);
        return PageResult.of(notificationMapper.listByUser(userId));
    }

    public Map<String, Object> unreadCount(Long userId) {
        Map<String, Object> map = new HashMap<>();
        map.put("count", notificationMapper.countUnread(userId));
        return map;
    }

    public void markRead(Long userId, Long id) {
        notificationMapper.markRead(id, userId);
    }

    public void markAllRead(Long userId) {
        notificationMapper.markAllRead(userId);
    }
}
