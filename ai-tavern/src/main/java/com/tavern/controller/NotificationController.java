package com.tavern.controller;

import com.tavern.common.Result;
import com.tavern.interceptor.UserContext;
import com.tavern.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public Result<?> list(@RequestParam(defaultValue = "1") int page,
                          @RequestParam(defaultValue = "20") int size) {
        return Result.ok(notificationService.list(UserContext.getUserId(), page, size));
    }

    @GetMapping("/unread/count")
    public Result<?> unread() {
        return Result.ok(notificationService.unreadCount(UserContext.getUserId()));
    }

    @PutMapping("/{id}/read")
    public Result<?> read(@PathVariable Long id) {
        notificationService.markRead(UserContext.getUserId(), id);
        return Result.ok();
    }

    @PutMapping("/read-all")
    public Result<?> readAll() {
        notificationService.markAllRead(UserContext.getUserId());
        return Result.ok();
    }
}
