package org.example.schoolshop.controller;

import lombok.RequiredArgsConstructor;
import org.example.schoolshop.common.Result;
import org.example.schoolshop.common.UserContext;
import org.example.schoolshop.service.NotificationService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/activities")
    public Result<Map<String, Object>> activities(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.ok(notificationService.listActivities(UserContext.requireUserId(), page, pageSize));
    }

    @GetMapping("/unread-count")
    public Result<Map<String, Integer>> unreadCount() {
        return Result.ok(notificationService.unreadCount(UserContext.requireUserId()));
    }

    @PostMapping("/activities/{id}/read")
    public Result<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(UserContext.requireUserId(), id);
        return Result.ok(null);
    }
}
