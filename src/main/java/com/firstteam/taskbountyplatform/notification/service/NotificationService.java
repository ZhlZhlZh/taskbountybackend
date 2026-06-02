package com.firstteam.taskbountyplatform.notification.service;

import com.firstteam.taskbountyplatform.auth.security.UserContext;
import com.firstteam.taskbountyplatform.notification.entity.Notification;
import com.firstteam.taskbountyplatform.common.enums.NotificationType;
import com.firstteam.taskbountyplatform.notification.repository.NotificationRepository;
import com.firstteam.taskbountyplatform.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * NotificationService - called by other services to create notifications.
 * Lightweight and fast, with anti-spam deduplication and batch support.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final UserContext userContext;

    public NotificationService(NotificationRepository notificationRepository,
                                UserRepository userRepository,
                                UserContext userContext) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.userContext = userContext;
    }

    /**
     * Core creation method called by other services.
     */
    @Transactional
    public Notification createNotification(Long receiverId, NotificationType type,
                                            String title, String content, String targetUrl) {
        if (!userRepository.existsById(receiverId)) {
            log.warn("Attempted to create notification for non-existent user: {}", receiverId);
            return null;
        }

        Notification notification = new Notification();
        notification.setReceiverId(receiverId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setTargetUrl(targetUrl);
        notification.setIsRead(false);
        notification.setCreatedAt(LocalDateTime.now());

        Notification saved = notificationRepository.save(notification);
        log.debug("Notification created: id={}, receiverId={}, type={}", saved.getId(), receiverId, type);
        return saved;
    }

    /**
     * Create notification with anti-spam deduplication on receiver+type+taskId
     * within last 30 minutes.
     */
    @Transactional
    public Notification createNotification(Long receiverId, NotificationType type,
                                            String title, String content, String targetUrl,
                                            Long taskId) {
        // Anti-spam: check if same receiver+type+taskId in last 30 minutes
        LocalDateTime thirtyMinutesAgo = LocalDateTime.now().minusMinutes(30);
        long recentCount = notificationRepository.countByReceiverIdAndTypeAndTaskIdAndCreatedAtAfter(
                receiverId, type.name(), taskId, thirtyMinutesAgo);

        if (recentCount > 0) {
            log.info("Duplicate notification suppressed: receiverId={}, type={}, taskId={}",
                    receiverId, type, taskId);
            return null;
        }

        Notification notification = new Notification();
        notification.setReceiverId(receiverId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setTargetUrl(targetUrl);
        notification.setTaskId(taskId);
        notification.setIsRead(false);
        notification.setCreatedAt(LocalDateTime.now());

        Notification saved = notificationRepository.save(notification);
        log.debug("Notification created: id={}, receiverId={}, type={}, taskId={}",
                saved.getId(), receiverId, type, taskId);
        return saved;
    }

    /**
     * Batch create notifications for multiple users (broadcasts, announcements).
     * Uses @Async for large batches to avoid blocking the caller.
     */
    @Async
    @Transactional
    public void createBatchNotifications(List<Long> receiverIds, NotificationType type,
                                          String title, String content, String targetUrl) {
        if (receiverIds == null || receiverIds.isEmpty()) {
            log.warn("createBatchNotifications called with empty receiver list");
            return;
        }

        List<Notification> batch = new ArrayList<>(receiverIds.size());
        LocalDateTime now = LocalDateTime.now();

        for (Long receiverId : receiverIds) {
            if (!userRepository.existsById(receiverId)) {
                log.debug("Skipping notification for non-existent user: {}", receiverId);
                continue;
            }
            Notification notification = new Notification();
            notification.setReceiverId(receiverId);
            notification.setType(type);
            notification.setTitle(title);
            notification.setContent(content);
            notification.setTargetUrl(targetUrl);
            notification.setIsRead(false);
            notification.setCreatedAt(now);
            batch.add(notification);
        }

        if (!batch.isEmpty()) {
            notificationRepository.saveAll(batch);
            log.info("Batch created {} notifications of type {}", batch.size(), type);
        }
    }

    /**
     * Get paginated notifications for a user, with optional type filter.
     */
    @Transactional(readOnly = true)
    public Page<Notification> getNotifications(Long userId, String type, Pageable pageable) {
        if (type != null && !type.isBlank()) {
            return notificationRepository.findByReceiverIdAndType(userId, type, pageable);
        }
        return notificationRepository.findByReceiverIdOrderByCreatedAtDesc(userId, pageable);
    }

    /**
     * Get top 5 unread notifications for the dropdown panel.
     */
    @Transactional(readOnly = true)
    public List<Notification> getUnreadNotifications(Long userId) {
        return notificationRepository.findTop5ByReceiverIdAndIsReadFalseOrderByCreatedAtDesc(userId);
    }

    /**
     * Get unread notification count, capped at 99+.
     */
    @Transactional(readOnly = true)
    public int getUnreadCount(Long userId) {
        long count = notificationRepository.countByReceiverIdAndIsReadFalse(userId);
        return (int) Math.min(count, 99);
    }

    /**
     * Mark specified notifications as read for a user.
     */
    @Transactional
    public int markAsRead(List<Long> notificationIds, Long userId) {
        if (notificationIds == null || notificationIds.isEmpty()) {
            return 0;
        }
        int updated = notificationRepository.markAsRead(notificationIds, LocalDateTime.now());
        log.debug("Marked {} notifications as read for user {}", updated, userId);
        return updated;
    }

    /**
     * Delete specified notifications for a user.
     */
    @Transactional
    public int deleteNotifications(List<Long> notificationIds, Long userId) {
        if (notificationIds == null || notificationIds.isEmpty()) {
            return 0;
        }
        int deleted = notificationRepository.deleteByIds(notificationIds, userId);
        log.debug("Deleted {} notifications for user {}", deleted, userId);
        return deleted;
    }

    /**
     * Mark all notifications as read for a user.
     */
    @Transactional
    public int markAllAsRead(Long userId) {
        // Get all unread notification IDs for this user
        Page<Notification> allNotifications = notificationRepository
                .findByReceiverIdOrderByCreatedAtDesc(userId, Pageable.unpaged());
        List<Long> unreadIds = allNotifications.getContent().stream()
                .filter(n -> !Boolean.TRUE.equals(n.getIsRead()))
                .map(Notification::getId)
                .toList();

        if (unreadIds.isEmpty()) {
            return 0;
        }

        int updated = notificationRepository.markAsRead(unreadIds, LocalDateTime.now());
        log.debug("Marked {} notifications as read for user {} (all)", updated, userId);
        return updated;
    }
}
