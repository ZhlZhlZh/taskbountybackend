package com.firstteam.taskbountyplatform.scheduler;

import com.firstteam.taskbountyplatform.audit.service.AuditLogService;
import com.firstteam.taskbountyplatform.common.enums.AuditActionType;
import com.firstteam.taskbountyplatform.common.enums.TaskStatus;
import com.firstteam.taskbountyplatform.common.enums.CreditChangeReason;
import com.firstteam.taskbountyplatform.credit.entity.CreditRecord;
import com.firstteam.taskbountyplatform.credit.entity.CreditRuleConfig;
import com.firstteam.taskbountyplatform.credit.repository.CreditRecordRepository;
import com.firstteam.taskbountyplatform.credit.repository.CreditRuleConfigRepository;
import com.firstteam.taskbountyplatform.credit.service.CreditService;
import com.firstteam.taskbountyplatform.common.enums.NotificationType;
import com.firstteam.taskbountyplatform.notification.service.NotificationService;
import com.firstteam.taskbountyplatform.review.entity.Review;
import com.firstteam.taskbountyplatform.common.enums.ReviewType;
import com.firstteam.taskbountyplatform.review.repository.ReviewRepository;
import com.firstteam.taskbountyplatform.review.service.ReviewService;
import com.firstteam.taskbountyplatform.task.entity.Task;
import com.firstteam.taskbountyplatform.task.repository.TaskRepository;
import com.firstteam.taskbountyplatform.user.entity.User;
import com.firstteam.taskbountyplatform.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Scheduler that handles review-related timeout operations:
 * auto-creating default reviews for completed tasks and daily credit score recalculations.
 */
@Component
public class ReviewTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReviewTimeoutScheduler.class);

    private final TaskRepository taskRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewService reviewService;
    private final CreditService creditService;
    private final CreditRecordRepository creditRecordRepository;
    private final CreditRuleConfigRepository creditRuleConfigRepository;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final UserRepository userRepository;

    public ReviewTimeoutScheduler(TaskRepository taskRepository,
                                  ReviewRepository reviewRepository,
                                  ReviewService reviewService,
                                  CreditService creditService,
                                  CreditRecordRepository creditRecordRepository,
                                  CreditRuleConfigRepository creditRuleConfigRepository,
                                  NotificationService notificationService,
                                  AuditLogService auditLogService,
                                  UserRepository userRepository) {
        this.taskRepository = taskRepository;
        this.reviewRepository = reviewRepository;
        this.reviewService = reviewService;
        this.creditService = creditService;
        this.creditRecordRepository = creditRecordRepository;
        this.creditRuleConfigRepository = creditRuleConfigRepository;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
        this.userRepository = userRepository;
    }

    /**
     * Every 15 minutes: find COMPLETED tasks older than 24 hours where users
     * haven't submitted reviews, and create default 5-star reviews for them.
     */
    @Scheduled(cron = "0 */15 * * * ?")
    public void createDefaultReviews() {
        log.info("Starting createDefaultReviews...");
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        int defaultReviewCount = 0;

        // Find all completed tasks older than 24 hours
        List<Task> completedTasks = taskRepository.findAll().stream()
                .filter(t -> t.getStatus() == TaskStatus.COMPLETED)
                .filter(t -> t.getCompletedAt() != null && t.getCompletedAt().isBefore(cutoff))
                .toList();

        log.info("Found {} COMPLETED tasks older than 24 hours", completedTasks.size());

        for (Task task : completedTasks) {
            try {
                int created = processDefaultReviewsForTask(task);
                defaultReviewCount += created;
            } catch (Exception e) {
                log.error("Failed to create default reviews for task id={}: {}",
                        task.getId(), e.getMessage(), e);
            }
        }
        log.info("createDefaultReviews completed. Created {} default reviews", defaultReviewCount);
    }

    @Transactional
    protected int processDefaultReviewsForTask(Task task) {
        int created = 0;

        // Check if publisher has already submitted a review for the worker
        Optional<Review> publisherReviewOpt = reviewRepository.findByTaskIdAndReviewerIdAndReviewType(
                task.getId(), task.getPublisherId(), ReviewType.PUBLISHER_TO_WORKER.name());

        if (publisherReviewOpt.isEmpty() && task.getWinnerId() != null) {
            createDefaultReview(task.getId(), task.getPublisherId(), task.getWinnerId(),
                    ReviewType.PUBLISHER_TO_WORKER);
            created++;
        }

        // Check if worker has already submitted a review for the publisher
        if (task.getWinnerId() != null) {
            Optional<Review> workerReviewOpt = reviewRepository.findByTaskIdAndReviewerIdAndReviewType(
                    task.getId(), task.getWinnerId(), ReviewType.WORKER_TO_PUBLISHER.name());

            if (workerReviewOpt.isEmpty()) {
                createDefaultReview(task.getId(), task.getWinnerId(), task.getPublisherId(),
                        ReviewType.WORKER_TO_PUBLISHER);
                created++;
            }
        }

        if (created > 0) {
            // Record audit log
            try {
                auditLogService.log(
                        AuditActionType.SYSTEM_AUTO_REVIEW,
                        "TASK",
                        task.getId(),
                        "Created " + created + " default 5-star review(s) for task #" + task.getId()
                );
            } catch (Exception e) {
                log.warn("Failed to record audit log for default reviews task id={}: {}",
                        task.getId(), e.getMessage());
            }
        }

        return created;
    }

    /**
     * Creates a single default review (5 stars, no text, isDefault=true).
     */
    protected void createDefaultReview(Long taskId, Long reviewerId, Long revieweeId, ReviewType reviewType) {
        Review review = new Review();
        review.setTaskId(taskId);
        review.setReviewerId(reviewerId);
        review.setRevieweeId(revieweeId);
        review.setReviewType(reviewType);
        review.setStars(5);
        review.setComment(null);
        review.setIsDefault(true);
        reviewRepository.save(review);

        log.debug("Created default review: task={} reviewer={} reviewee={} type={}",
                taskId, reviewerId, revieweeId, reviewType);
    }

    /**
     * Daily at midnight: recalculate credit scores for all users who participated
     * in tasks completed today. This processes completion rate, praise rate,
     * and applies credit score changes.
     */
    @Scheduled(cron = "0 0 0 * * ?")
    @Transactional
    public void recalculateCreditScores() {
        log.info("Starting recalculateCreditScores...");
        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIDNIGHT);
        LocalDateTime now = LocalDateTime.now();

        // Find all tasks completed today
        List<Task> todayCompletedTasks = taskRepository.findAll().stream()
                .filter(t -> t.getStatus() == TaskStatus.COMPLETED)
                .filter(t -> t.getCompletedAt() != null
                        && !t.getCompletedAt().isBefore(todayStart)
                        && !t.getCompletedAt().isAfter(now))
                .toList();

        log.info("Found {} tasks completed today", todayCompletedTasks.size());

        // Collect all affected user IDs
        Set<Long> affectedUserIds = new HashSet<>();
        for (Task task : todayCompletedTasks) {
            if (task.getPublisherId() != null) {
                affectedUserIds.add(task.getPublisherId());
            }
            if (task.getWinnerId() != null) {
                affectedUserIds.add(task.getWinnerId());
            }
        }

        log.info("Recalculating credit scores for {} users", affectedUserIds.size());

        for (Long userId : affectedUserIds) {
            try {
                recalculateUserCreditScore(userId);
            } catch (Exception e) {
                log.error("Failed to recalculate credit score for user id={}: {}", userId, e.getMessage(), e);
            }
        }
        log.info("recalculateCreditScores completed");
    }

    @Transactional
    protected void recalculateUserCreditScore(Long userId) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            log.warn("User id={} not found during credit recalculation", userId);
            return;
        }
        User user = userOpt.get();
        int oldScore = user.getCreditScore();

        // Calculate completion rate
        // completion_rate = completed tasks / total accepted tasks * 100
        List<Task> allTasks = taskRepository.findByWinnerId(userId);
        long totalAccepted = allTasks.size();
        long completedCount = allTasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.COMPLETED)
                .count();
        int completionRate = totalAccepted > 0
                ? (int) (completedCount * 100 / totalAccepted)
                : 100;

        // Calculate praise rate from reviews
        // praise_rate = count of reviews with stars >= 4 / total reviews * 100
        long goodReviewsAsWorker = reviewRepository.countGoodReviews(userId,
                ReviewType.PUBLISHER_TO_WORKER.name());
        long totalReviewsAsWorker = reviewRepository.countTotalReviews(userId,
                ReviewType.PUBLISHER_TO_WORKER.name());

        long goodReviewsAsPublisher = reviewRepository.countGoodReviews(userId,
                ReviewType.WORKER_TO_PUBLISHER.name());
        long totalReviewsAsPublisher = reviewRepository.countTotalReviews(userId,
                ReviewType.WORKER_TO_PUBLISHER.name());

        long totalGoodReviews = goodReviewsAsWorker + goodReviewsAsPublisher;
        long totalReviews = totalReviewsAsWorker + totalReviewsAsPublisher;
        int praiseRate = totalReviews > 0
                ? (int) (totalGoodReviews * 100 / totalReviews)
                : 100;

        // Apply credit score changes based on thresholds
        int scoreDelta = 0;
        String changeReasonDescription = "";

        // Completion rate thresholds
        Optional<CreditRuleConfig> completionRuleOpt =
                creditRuleConfigRepository.findByRuleKey("COMPLETION_RATE_THRESHOLD");
        if (completionRuleOpt.isPresent() && completionRuleOpt.get().getEnabled()) {
            int goodThreshold = Integer.parseInt(completionRuleOpt.get().getThresholdValue());
            int goodScore = completionRuleOpt.get().getScoreDelta();

            if (completionRate >= goodThreshold) {
                scoreDelta += goodScore;
            } else {
                // Check bad threshold
                Optional<CreditRuleConfig> badRuleOpt =
                        creditRuleConfigRepository.findByRuleKey("COMPLETION_RATE_BAD_THRESHOLD");
                if (badRuleOpt.isPresent() && badRuleOpt.get().getEnabled()) {
                    int badThreshold = Integer.parseInt(badRuleOpt.get().getThresholdValue());
                    if (completionRate < badThreshold) {
                        scoreDelta += badRuleOpt.get().getScoreDelta();
                    }
                }
            }
        }

        // Praise rate thresholds
        Optional<CreditRuleConfig> praiseRuleOpt =
                creditRuleConfigRepository.findByRuleKey("PRAISE_RATE_THRESHOLD");
        if (praiseRuleOpt.isPresent() && praiseRuleOpt.get().getEnabled()) {
            int goodThreshold = Integer.parseInt(praiseRuleOpt.get().getThresholdValue());
            int goodScore = praiseRuleOpt.get().getScoreDelta();

            if (praiseRate >= goodThreshold) {
                scoreDelta += goodScore;
            } else {
                Optional<CreditRuleConfig> badRuleOpt =
                        creditRuleConfigRepository.findByRuleKey("PRAISE_RATE_BAD_THRESHOLD");
                if (badRuleOpt.isPresent() && badRuleOpt.get().getEnabled()) {
                    int badThreshold = Integer.parseInt(badRuleOpt.get().getThresholdValue());
                    if (praiseRate < badThreshold) {
                        scoreDelta += badRuleOpt.get().getScoreDelta();
                    }
                }
            }
        }

        // Apply the score change
        if (scoreDelta != 0) {
            int newScore = oldScore + scoreDelta;
            newScore = Math.max(0, Math.min(100, newScore));

            user.setCreditScore(newScore);
            userRepository.save(user);

            // Determine reason
            CreditChangeReason reason;
            if (scoreDelta > 0) {
                reason = CreditChangeReason.COMPLETION_RATE_GOOD;
            } else {
                reason = CreditChangeReason.COMPLETION_RATE_BAD;
            }

            // Record credit change
            CreditRecord record = new CreditRecord();
            record.setUserId(userId);
            record.setTaskId(null); // daily recalculation is system-wide
            record.setChangeScore(scoreDelta);
            record.setReasonType(reason);
            record.setBeforeScore(oldScore);
            record.setAfterScore(newScore);
            record.setDescription("Daily recalculation: completionRate=" + completionRate
                    + "%, praiseRate=" + praiseRate + "%");
            creditRecordRepository.save(record);

            log.info("Credit score updated for user {}: {} -> {} (delta={}, completionRate={}%, praiseRate={}%)",
                    userId, oldScore, newScore, scoreDelta, completionRate, praiseRate);

            // Notify user if score dropped significantly
            if (scoreDelta < -3) {
                try {
                    notificationService.createNotification(
                            userId,
                            NotificationType.CREDIT_CHANGE,
                            "信用分变更提醒",
                            "您的信用分因系统定期评估发生变化：" + oldScore + " -> " + newScore
                                    + "（变化：" + scoreDelta + "）。完成率：" + completionRate
                                    + "%，好评率：" + praiseRate + "%",
                            "/user/profile"
                    );
                } catch (Exception e) {
                    log.warn("Failed to send credit change notification to user id={}: {}",
                            userId, e.getMessage());
                }
            }
        } else {
            log.debug("No credit score change for user {}: score={}, completionRate={}%, praiseRate={}%",
                    userId, oldScore, completionRate, praiseRate);
        }
    }
}
