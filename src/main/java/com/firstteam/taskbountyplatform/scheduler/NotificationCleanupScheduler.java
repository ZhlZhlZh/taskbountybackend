package com.firstteam.taskbountyplatform.scheduler;

import com.firstteam.taskbountyplatform.notification.entity.Notification;
import com.firstteam.taskbountyplatform.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Scheduler that cleans up old read notifications to prevent database bloat.
 * Unread notifications are kept indefinitely.
 */
@Component
public class NotificationCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationCleanupScheduler.class);

    /** Notifications older than this many days are eligible for cleanup (if read) */
    private static final int READ_NOTIFICATION_RETENTION_DAYS = 90;

    /** Batch size for paginated deletion to avoid memory issues on large tables */
    private static final int BATCH_SIZE = 500;

    private final NotificationRepository notificationRepository;

    public NotificationCleanupScheduler(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /**
     * Daily at 3:00 AM: delete read notifications older than 90 days.
     * Unread notifications are never deleted.
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void cleanOldNotifications() {
        log.info("Starting cleanOldNotifications...");
        LocalDateTime cutoff = LocalDateTime.now().minusDays(READ_NOTIFICATION_RETENTION_DAYS);

        int totalDeleted = 0;
        int page = 0;
        boolean hasMore = true;

        while (hasMore) {
            try {
                // Fetch a page of notifications created before the cutoff
                Page<Notification> notificationPage = notificationRepository.findAll(
                        PageRequest.of(page, BATCH_SIZE));

                if (notificationPage.isEmpty()) {
                    hasMore = false;
                    break;
                }

                // Filter to only read notifications older than the cutoff
                List<Long> idsToDelete = new ArrayList<>();
                for (Notification notification : notificationPage.getContent()) {
                    if (Boolean.TRUE.equals(notification.getIsRead())
                            && notification.getCreatedAt() != null
                            && notification.getCreatedAt().isBefore(cutoff)) {
                        idsToDelete.add(notification.getId());
                    }
                }

                if (!idsToDelete.isEmpty()) {
                    // Delete in batch
                    notificationRepository.deleteAllById(idsToDelete);
                    totalDeleted += idsToDelete.size();
                    log.debug("Batch {}: deleted {} old read notifications", page, idsToDelete.size());
                }

                // Check if we've processed all pages
                if (notificationPage.isLast()) {
                    hasMore = false;
                } else {
                    page++;
                }

            } catch (Exception e) {
                log.error("Failed to clean notifications on page {}: {}", page, e.getMessage(), e);
                hasMore = false;
            }
        }

        log.info("cleanOldNotifications completed. Deleted {} read notifications older than {} days",
                totalDeleted, READ_NOTIFICATION_RETENTION_DAYS);
    }
}
