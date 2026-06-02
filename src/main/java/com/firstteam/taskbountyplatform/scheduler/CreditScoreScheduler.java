package com.firstteam.taskbountyplatform.scheduler;

import com.firstteam.taskbountyplatform.common.enums.TaskStatus;
import com.firstteam.taskbountyplatform.config.PlatformConfig;
import com.firstteam.taskbountyplatform.common.enums.CreditChangeReason;
import com.firstteam.taskbountyplatform.credit.entity.CreditRecord;
import com.firstteam.taskbountyplatform.credit.entity.CreditRuleConfig;
import com.firstteam.taskbountyplatform.credit.repository.CreditRecordRepository;
import com.firstteam.taskbountyplatform.credit.repository.CreditRuleConfigRepository;
import com.firstteam.taskbountyplatform.common.enums.NotificationType;
import com.firstteam.taskbountyplatform.notification.service.NotificationService;
import com.firstteam.taskbountyplatform.task.entity.Task;
import com.firstteam.taskbountyplatform.task.repository.TaskRepository;
import com.firstteam.taskbountyplatform.user.entity.User;
import com.firstteam.taskbountyplatform.common.enums.UserStatus;
import com.firstteam.taskbountyplatform.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Scheduler for credit-score-related operations:
 * recalculating completion rates for all active users
 * and checking/unfreezing expired temporary account freezes.
 */
@Component
public class CreditScoreScheduler {

    private static final Logger log = LoggerFactory.getLogger(CreditScoreScheduler.class);

    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final CreditRecordRepository creditRecordRepository;
    private final CreditRuleConfigRepository creditRuleConfigRepository;
    private final PlatformConfig platformConfig;
    private final NotificationService notificationService;

    public CreditScoreScheduler(UserRepository userRepository,
                                TaskRepository taskRepository,
                                CreditRecordRepository creditRecordRepository,
                                CreditRuleConfigRepository creditRuleConfigRepository,
                                PlatformConfig platformConfig,
                                NotificationService notificationService) {
        this.userRepository = userRepository;
        this.taskRepository = taskRepository;
        this.creditRecordRepository = creditRecordRepository;
        this.creditRuleConfigRepository = creditRuleConfigRepository;
        this.platformConfig = platformConfig;
        this.notificationService = notificationService;
    }

    /**
     * Daily at 2:00 AM: recalculate completion rates for all users who have
     * active task participation. Apply credit score rules based on thresholds.
     * Records CreditRecord entries if score changed.
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void recalculateAllCompletionRates() {
        log.info("Starting recalculateAllCompletionRates...");

        // Get all users and identify those with active task participation
        List<User> allUsers = userRepository.findAll();

        // Collect users who have published or won tasks
        Set<Long> activeUserIds = new HashSet<>();
        List<Task> allTasks = taskRepository.findAll();
        for (Task task : allTasks) {
            if (task.getPublisherId() != null) {
                activeUserIds.add(task.getPublisherId());
            }
            if (task.getWinnerId() != null) {
                activeUserIds.add(task.getWinnerId());
            }
        }

        // Filter to users who have actual task participation
        List<User> activeUsers = allUsers.stream()
                .filter(u -> activeUserIds.contains(u.getId()))
                .toList();

        log.info("Recalculating completion rates for {} active users (out of {} total)",
                activeUsers.size(), allUsers.size());

        int scoreChangedCount = 0;
        for (User user : activeUsers) {
            try {
                if (recalculateUserCompletionRate(user)) {
                    scoreChangedCount++;
                }
            } catch (Exception e) {
                log.error("Failed to recalculate completion rate for user id={}: {}",
                        user.getId(), e.getMessage(), e);
            }
        }

        log.info("recalculateAllCompletionRates completed. Score changed for {} users", scoreChangedCount);
    }

    /**
     * Recalculate a single user's completion rate and apply credit score rules.
     * Returns true if the credit score was changed.
     */
    @Transactional
    protected boolean recalculateUserCompletionRate(User user) {
        // Get all tasks where this user is the worker (winner)
        List<Task> userTasks = taskRepository.findByWinnerId(user.getId());
        if (userTasks.isEmpty()) {
            // Also check as publisher for task creation stats
            List<Task> publishedTasks = taskRepository.findByPublisherId(user.getId());
            if (publishedTasks.isEmpty()) {
                log.debug("User id={} has no task participation, skipping", user.getId());
                return false;
            }
        }

        long totalAccepted = userTasks.size();
        long completedCount = userTasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.COMPLETED)
                .count();
        long cancelledCount = userTasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.CANCELLED)
                .count();

        if (totalAccepted == 0) {
            log.debug("User id={} has no accepted tasks (winner), skipping", user.getId());
            return false;
        }

        int completionRate = (int) (completedCount * 100 / totalAccepted);
        int oldScore = user.getCreditScore();

        // Apply credit rules based on completion rate thresholds
        int scoreDelta = 0;
        String reasonDescription = "";

        // Get completion rate rules
        Optional<CreditRuleConfig> goodRuleOpt =
                creditRuleConfigRepository.findByRuleKey("COMPLETION_RATE_THRESHOLD");
        Optional<CreditRuleConfig> badRuleOpt =
                creditRuleConfigRepository.findByRuleKey("COMPLETION_RATE_BAD_THRESHOLD");

        // Apply good threshold
        if (goodRuleOpt.isPresent() && goodRuleOpt.get().getEnabled()) {
            int goodThreshold;
            try {
                goodThreshold = Integer.parseInt(goodRuleOpt.get().getThresholdValue());
            } catch (NumberFormatException e) {
                goodThreshold = platformConfig.getCredit().getCompletionRateGood();
            }
            int goodScoreDelta = goodRuleOpt.get().getScoreDelta();

            if (completionRate >= goodThreshold && user.getCreditScore() < platformConfig.getCredit().getMaxScore()) {
                scoreDelta += goodScoreDelta;
                reasonDescription = "Completion rate good (>= " + goodThreshold + "%)";
            }
        }

        // Apply bad threshold
        if (badRuleOpt.isPresent() && badRuleOpt.get().getEnabled()) {
            int badThreshold;
            try {
                badThreshold = Integer.parseInt(badRuleOpt.get().getThresholdValue());
            } catch (NumberFormatException e) {
                badThreshold = platformConfig.getCredit().getCompletionRateBad();
            }
            int badScoreDelta = badRuleOpt.get().getScoreDelta();

            if (completionRate < badThreshold && user.getCreditScore() > platformConfig.getCredit().getMinScore()) {
                scoreDelta += badScoreDelta;
                reasonDescription = "Completion rate bad (< " + badThreshold + "%)";
            }
        }

        // If both triggered, combine the description
        if (scoreDelta > 0 && scoreDelta < 0) {
            reasonDescription = "Completion rate mixed evaluation";
        }

        // Apply the score change
        if (scoreDelta != 0) {
            int newScore = oldScore + scoreDelta;
            newScore = Math.max(platformConfig.getCredit().getMinScore(),
                    Math.min(platformConfig.getCredit().getMaxScore(), newScore));

            user.setCreditScore(newScore);
            userRepository.save(user);

            // Determine credit change reason
            CreditChangeReason reason = scoreDelta > 0
                    ? CreditChangeReason.COMPLETION_RATE_GOOD
                    : CreditChangeReason.COMPLETION_RATE_BAD;

            // Record the credit change
            CreditRecord record = new CreditRecord();
            record.setUserId(user.getId());
            record.setTaskId(null);
            record.setChangeScore(scoreDelta);
            record.setReasonType(reason);
            record.setBeforeScore(oldScore);
            record.setAfterScore(newScore);
            record.setDescription(String.format(
                    "Daily completion rate recalculation: rate=%d%% (completed=%d/total=%d, cancelled=%d). %s",
                    completionRate, completedCount, totalAccepted, cancelledCount, reasonDescription));
            creditRecordRepository.save(record);

            log.info("Credit score updated for user {}: {} -> {} (delta={}, completionRate={}%)",
                    user.getId(), oldScore, newScore, scoreDelta, completionRate);

            // Notify user of credit change
            try {
                notificationService.createNotification(
                        user.getId(),
                        NotificationType.CREDIT_CHANGE,
                        "信用分月度更新",
                        "您的信用分已更新：" + oldScore + " -> " + newScore
                                + "（变化：" + (scoreDelta > 0 ? "+" : "") + scoreDelta
                                + "）。完成率：" + completionRate + "%"
                                + "（完成 " + completedCount + "/" + totalAccepted + " 个任务）",
                        "/user/profile"
                );
            } catch (Exception e) {
                log.warn("Failed to send credit change notification to user id={}: {}",
                        user.getId(), e.getMessage());
            }

            return true;
        }

        return false;
    }

    /**
     * Daily at midnight: check for users whose account freeze period has expired.
     * If graduated: keep frozen (permanent freeze).
     * If temporary freeze (from report penalty): unfreeze and notify.
     */
    @Scheduled(cron = "0 0 0 * * ?")
    @Transactional
    public void checkFrozenAccountExpiry() {
        log.info("Starting checkFrozenAccountExpiry...");
        LocalDateTime now = LocalDateTime.now();

        List<User> frozenUsers = userRepository.findByAccountStatus(UserStatus.FROZEN,
                        PageRequest.of(0, Integer.MAX_VALUE))
                .stream()
                .filter(u -> u.getFrozenUntil() != null && u.getFrozenUntil().isBefore(now))
                .toList();

        if (frozenUsers.isEmpty()) {
            log.info("No frozen accounts with expired freeze period found");
            return;
        }

        log.info("Found {} frozen accounts with expired freeze period", frozenUsers.size());

        int unfrozenCount = 0;
        int keptFrozenCount = 0;

        for (User user : frozenUsers) {
            try {
                if (Boolean.TRUE.equals(user.getGraduated())) {
                    // Graduated users stay frozen permanently
                    // Just log that we checked but kept frozen
                    log.info("User id={} is graduated, keeping account permanently frozen", user.getId());
                    keptFrozenCount++;
                } else {
                    // Temporary freeze (e.g., from report penalty) - unfreeze
                    unfreezeUser(user, now);
                    unfrozenCount++;
                }
            } catch (Exception e) {
                log.error("Failed to process frozen expiry for user id={}: {}",
                        user.getId(), e.getMessage(), e);
            }
        }

        log.info("checkFrozenAccountExpiry completed. Unfrozen: {}, Kept frozen (graduated): {}",
                unfrozenCount, keptFrozenCount);
    }

    /**
     * Unfreeze a temporarily frozen user account.
     */
    protected void unfreezeUser(User user, LocalDateTime now) {
        log.info("Unfreezing user id={} (was frozen until {}, reason: {})",
                user.getId(), user.getFrozenUntil(), user.getFreezeReason());

        user.setAccountStatus(UserStatus.NORMAL);
        user.setFrozenUntil(null);
        user.setFreezeReason(null);
        userRepository.save(user);

        // Notify user of unfreeze
        try {
            notificationService.createNotification(
                    user.getId(),
                    NotificationType.SYSTEM_NOTICE,
                    "账户已解冻",
                    "您的账户已于 " + now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                            + " 自动解冻，现已恢复正常使用。",
                    "/user/profile"
            );
        } catch (Exception e) {
            log.warn("Failed to send unfreeze notification to user id={}: {}", user.getId(), e.getMessage());
        }

        log.info("Successfully unfroze user id={}", user.getId());
    }
}
