package com.firstteam.taskbountyplatform.notification.controller;

import com.firstteam.taskbountyplatform.auth.security.UserContext;
import com.firstteam.taskbountyplatform.common.response.ApiResponse;
import com.firstteam.taskbountyplatform.common.response.PageResult;
import com.firstteam.taskbountyplatform.notification.entity.Notification;
import com.firstteam.taskbountyplatform.notification.service.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * NotificationController - REST API for notification management.
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final UserContext userContext;

    public NotificationController(NotificationService notificationService,
                                   UserContext userContext) {
        this.notificationService = notificationService;
        this.userContext = userContext;
    }

    /**
     * GET /api/notifications - get paginated notifications with optional type filter.
     */
    @GetMapping
    public ApiResponse<PageResult<Notification>> getNotifications(
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int size) {
        Long userId = userContext.getCurrentUserId();
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Notification> result = notificationService.getNotifications(userId, type, pageable);
        PageResult<Notification> pageResult = new PageResult<>(
                result.getContent(), page, size, result.getTotalElements());
        return ApiResponse.success(pageResult);
    }

    /**
     * GET /api/notifications/unread - get top 5 unread notifications.
     */
    @GetMapping("/unread")
    public ApiResponse<List<Notification>> getUnreadNotifications() {
        Long userId = userContext.getCurrentUserId();
        List<Notification> unread = notificationService.getUnreadNotifications(userId);
        return ApiResponse.success(unread);
    }

    /**
     * GET /api/notifications/unread-count - get unread notification count.
     */
    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Integer>> getUnreadCount() {
        Long userId = userContext.getCurrentUserId();
        int count = notificationService.getUnreadCount(userId);
        return ApiResponse.success(Map.of("count", count));
    }

    /**
     * PUT /api/notifications/read - mark specified notifications as read.
     */
    @PutMapping("/read")
    public ApiResponse<Map<String, Integer>> markAsRead(@RequestBody Map<String, List<Long>> body) {
        Long userId = userContext.getCurrentUserId();
        List<Long> ids = body.get("ids");
        int updated = notificationService.markAsRead(ids, userId);
        return ApiResponse.success(Map.of("updated", updated));
    }

    /**
     * PUT /api/notifications/read-all - mark all notifications as read.
     */
    @PutMapping("/read-all")
    public ApiResponse<Map<String, Integer>> markAllAsRead() {
        Long userId = userContext.getCurrentUserId();
        int updated = notificationService.markAllAsRead(userId);
        return ApiResponse.success(Map.of("updated", updated));
    }

    /**
     * DELETE /api/notifications - delete specified notifications.
     */
    @DeleteMapping
    public ApiResponse<Map<String, Integer>> deleteNotifications(@RequestBody Map<String, List<Long>> body) {
        Long userId = userContext.getCurrentUserId();
        List<Long> ids = body.get("ids");
        int deleted = notificationService.deleteNotifications(ids, userId);
        return ApiResponse.success(Map.of("deleted", deleted));
    }
}
