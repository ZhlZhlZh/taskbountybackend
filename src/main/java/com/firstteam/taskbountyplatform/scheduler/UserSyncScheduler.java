package com.firstteam.taskbountyplatform.scheduler;

import com.firstteam.taskbountyplatform.audit.service.AuditLogService;
import com.firstteam.taskbountyplatform.common.enums.AuditActionType;
import com.firstteam.taskbountyplatform.config.PlatformConfig;
import com.firstteam.taskbountyplatform.user.entity.User;
import com.firstteam.taskbountyplatform.common.enums.UserStatus;
import com.firstteam.taskbountyplatform.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Scheduler that handles user information synchronization from the BIT school system.
 * Also manages graduated student account freezing and retry of failed operations.
 */
@Component
public class UserSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(UserSyncScheduler.class);

    /** Graduation-related grade keywords (Chinese university system) */
    private static final List<String> GRADUATION_KEYWORDS = Arrays.asList(
            "大四", "研三", "博四", "博五", "毕业班",
            "四年级", "三年级研究生", "senior", "graduating"
    );

    /** Maximum retry attempts for failed sync operations */
    private static final int MAX_RETRY_ATTEMPTS = 3;
    /** Retry backoff: first wait 5 minutes, then 15 minutes */
    private static final long[] RETRY_BACKOFF_MINUTES = {5, 15};

    private final UserRepository userRepository;
    private final PlatformConfig platformConfig;
    private final AuditLogService auditLogService;

    /** Tracks failed sync operations for retry */
    private final Map<String, FailedSyncOperation> failedOperations = new ConcurrentHashMap<>();

    public UserSyncScheduler(UserRepository userRepository,
                             PlatformConfig platformConfig,
                             AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.platformConfig = platformConfig;
        this.auditLogService = auditLogService;
    }

    /**
     * Monthly (default: 1st at 3:00 AM): synchronize user information from the BIT school system.
     * Marks graduated students and freezes their accounts.
     * Cron is configurable via platform.user-sync-cron property.
     */
    @Scheduled(cron = "${platform.user-sync-cron:0 0 3 1 * ?}")
    @Transactional
    public void syncUserInfo() {
        log.info("Starting syncUserInfo...");
        LocalDateTime now = LocalDateTime.now();

        try {
            // Simulated: In production, this would call the BIT school API
            log.info("Simulating BIT school API sync...");
            simulateBitSchoolApiSync();

            // Mark graduated students
            int graduatedCount = markGraduatedStudents(now);
            log.info("Marked {} students as graduated", graduatedCount);

            // Freeze graduated accounts
            int frozenCount = freezeGraduatedAccounts(now);
            log.info("Froze {} graduated accounts", frozenCount);

            // Record audit log
            try {
                auditLogService.log(
                        AuditActionType.SYSTEM_INFO_SYNC,
                        "USER",
                        null,
                        "Monthly user info sync completed. Graduated: " + graduatedCount
                                + ", Frozen: " + frozenCount + " at "
                                + now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                );
            } catch (Exception e) {
                log.warn("Failed to record sync audit log: {}", e.getMessage());
            }

            // Clear any previously failed sync operations since this one succeeded
            failedOperations.remove("USER_SYNC");

        } catch (Exception e) {
            log.error("User sync failed: {}", e.getMessage(), e);
            // Track for retry
            FailedSyncOperation failedOp = failedOperations.computeIfAbsent(
                    "USER_SYNC", k -> new FailedSyncOperation("USER_SYNC", 0));
            failedOp.incrementAttempts();

            if (failedOp.getAttempts() <= MAX_RETRY_ATTEMPTS) {
                log.warn("User sync failed, will retry (attempt {}/{})",
                        failedOp.getAttempts(), MAX_RETRY_ATTEMPTS);
            } else {
                log.error("User sync failed after {} attempts, sending admin alert", MAX_RETRY_ATTEMPTS);
                sendAdminAlert("用户信息同步失败", "用户信息同步在 " + MAX_RETRY_ATTEMPTS
                        + " 次重试后仍然失败: " + e.getMessage());
            }
        }

        log.info("syncUserInfo completed");
    }

    /**
     * Simulate calling the BIT school API to sync user information.
     * In a real implementation, this would make HTTP calls to the BIT student
     * information system and update user records accordingly.
     */
    protected void simulateBitSchoolApiSync() {
        // For the prototype, just log that sync would happen
        log.info("BIT school API sync simulation: would fetch student data for all registered students.");
        log.info("In production, this would update real_name, grade, college, academy, email, phone fields.");
    }

    /**
     * Find users whose grade indicates graduation (e.g., "大四", "研三", specific year ranges)
     * and mark them as graduated.
     */
    protected int markGraduatedStudents(LocalDateTime now) {
        List<User> allUsers = userRepository.findAll();
        int markedCount = 0;

        for (User user : allUsers) {
            if (Boolean.TRUE.equals(user.getGraduated())) {
                continue; // Already marked as graduated
            }

            if (user.getGrade() != null && isGraduationGrade(user.getGrade())) {
                user.setGraduated(true);
                userRepository.save(user);
                markedCount++;
                log.info("Marked user id={} (grade={}) as graduated", user.getId(), user.getGrade());
            }
        }

        return markedCount;
    }

    /**
     * Check if a grade string indicates graduation.
     */
    protected boolean isGraduationGrade(String grade) {
        if (grade == null || grade.isBlank()) {
            return false;
        }
        String lowerGrade = grade.toLowerCase().trim();
        return GRADUATION_KEYWORDS.stream().anyMatch(k -> lowerGrade.contains(k.toLowerCase()));
    }

    /**
     * Freeze accounts of all graduated users who are not already frozen.
     */
    protected int freezeGraduatedAccounts(LocalDateTime now) {
        List<User> graduatedUsers = userRepository.findByGraduatedTrueAndAccountStatus(UserStatus.NORMAL);
        int freezeDurationDays = platformConfig.getFreezeDurationDays();

        int frozenCount = 0;
        for (User user : graduatedUsers) {
            try {
                user.setAccountStatus(UserStatus.FROZEN);
                user.setFrozenUntil(now.plusDays(freezeDurationDays));
                user.setFreezeReason("Graduated student - account frozen per platform policy. "
                        + "Frozen until: " + user.getFrozenUntil());
                user.setGraduationFreezeCount(
                        (user.getGraduationFreezeCount() != null ? user.getGraduationFreezeCount() : 0) + 1
                );
                userRepository.save(user);
                frozenCount++;

                // Record individual freeze audit
                try {
                    auditLogService.log(
                            AuditActionType.SYSTEM_FREEZE_GRADUATED,
                            "USER",
                            user.getId(),
                            "Graduated user account frozen until "
                                    + user.getFrozenUntil().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    );
                } catch (Exception e) {
                    log.warn("Failed to record freeze audit for user id={}: {}", user.getId(), e.getMessage());
                }
            } catch (Exception e) {
                log.error("Failed to freeze graduated user id={}: {}", user.getId(), e.getMessage(), e);
            }
        }

        return frozenCount;
    }

    /**
     * Retry failed sync operations with exponential backoff.
     * Called at regular intervals to retry pending operations.
     */
    @Scheduled(cron = "0 */10 * * * ?")
    @Transactional
    public void retryFailedSync() {
        if (failedOperations.isEmpty()) {
            return;
        }

        log.info("Checking {} failed sync operations for retry...", failedOperations.size());

        List<String> completedOps = new ArrayList<>();

        for (Map.Entry<String, FailedSyncOperation> entry : failedOperations.entrySet()) {
            FailedSyncOperation op = entry.getValue();

            // Check if we should retry based on backoff timing
            long backoffMinutes;
            if (op.getAttempts() <= RETRY_BACKOFF_MINUTES.length) {
                backoffMinutes = RETRY_BACKOFF_MINUTES[op.getAttempts() - 1];
            } else {
                backoffMinutes = RETRY_BACKOFF_MINUTES[RETRY_BACKOFF_MINUTES.length - 1];
            }

            if (op.getLastAttemptTime() != null) {
                long minutesSinceLastAttempt = java.time.Duration.between(
                        op.getLastAttemptTime(), LocalDateTime.now()).toMinutes();
                if (minutesSinceLastAttempt < backoffMinutes) {
                    log.debug("Skipping retry for '{}': backoff {} min not yet elapsed ({} min since last attempt)",
                            op.getOperationName(), backoffMinutes, minutesSinceLastAttempt);
                    continue;
                }
            }

            // Retry the operation
            log.info("Retrying failed operation '{}' (attempt {}/{})",
                    op.getOperationName(), op.getAttempts() + 1, MAX_RETRY_ATTEMPTS + 1);

            try {
                if ("USER_SYNC".equals(op.getOperationName())) {
                    syncUserInfo();
                }
                // Operation succeeded, remove from failed list
                completedOps.add(entry.getKey());
                log.info("Failed operation '{}' succeeded on retry", op.getOperationName());
            } catch (Exception e) {
                op.incrementAttempts();
                log.error("Retry failed for '{}': {}", op.getOperationName(), e.getMessage());

                if (op.getAttempts() > MAX_RETRY_ATTEMPTS) {
                    sendAdminAlert("重试失败",
                            "操作 '" + op.getOperationName() + "' 在 " + MAX_RETRY_ATTEMPTS
                                    + " 次重试后仍然失败: " + e.getMessage());
                    completedOps.add(entry.getKey());
                }
            }
        }

        // Remove completed/failed-beyond-retry operations
        completedOps.forEach(failedOperations::remove);

        if (!failedOperations.isEmpty()) {
            log.info("{} failed operations remaining for future retry", failedOperations.size());
        }
    }

    /**
     * Send an alert to admin. In production, this would send email/SMS/in-app notification.
     */
    protected void sendAdminAlert(String title, String content) {
        log.warn("ADMIN ALERT - {}: {}", title, content);
        // In production, integrate with admin notification system
        // e.g., find admins and send notification
        try {
            List<User> admins = userRepository.findAll().stream()
                    .filter(u -> u.getRole() != null
                            && "ADMIN".equalsIgnoreCase(
                                    u.getRole().name() != null ? u.getRole().name() : ""))
                    .toList();
            // For prototype, just log
            log.info("Would send admin alert to {} admins: [{}] {}", admins.size(), title, content);
        } catch (Exception e) {
            log.warn("Failed to look up admins for alert: {}", e.getMessage());
        }
    }

    /**
     * Internal class to track failed sync operations for retry.
     */
    private static class FailedSyncOperation {
        private final String operationName;
        private int attempts;
        private LocalDateTime lastAttemptTime;

        FailedSyncOperation(String operationName, int attempts) {
            this.operationName = operationName;
            this.attempts = attempts;
        }

        void incrementAttempts() {
            this.attempts++;
            this.lastAttemptTime = LocalDateTime.now();
        }

        String getOperationName() { return operationName; }
        int getAttempts() { return attempts; }
        LocalDateTime getLastAttemptTime() { return lastAttemptTime; }
    }
}
