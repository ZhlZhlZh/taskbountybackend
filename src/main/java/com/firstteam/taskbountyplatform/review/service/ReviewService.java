package com.firstteam.taskbountyplatform.review.service;

import com.firstteam.taskbountyplatform.common.enums.AuditActionType;
import com.firstteam.taskbountyplatform.audit.service.AuditLogService;
import com.firstteam.taskbountyplatform.auth.security.UserContext;
import com.firstteam.taskbountyplatform.common.enums.NotificationType;
import com.firstteam.taskbountyplatform.common.enums.TaskStatus;
import com.firstteam.taskbountyplatform.common.exception.BusinessException;
import com.firstteam.taskbountyplatform.credit.service.CreditService;
import com.firstteam.taskbountyplatform.notification.service.NotificationService;
import com.firstteam.taskbountyplatform.review.dto.ReviewSubmitRequest;
import com.firstteam.taskbountyplatform.review.entity.Review;
import com.firstteam.taskbountyplatform.common.enums.ReviewType;
import com.firstteam.taskbountyplatform.review.repository.ReviewRepository;
import com.firstteam.taskbountyplatform.task.entity.Task;
import com.firstteam.taskbountyplatform.task.repository.TaskRepository;
import com.firstteam.taskbountyplatform.user.entity.User;
import com.firstteam.taskbountyplatform.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final CreditService creditService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final UserContext userContext;

    public ReviewService(ReviewRepository reviewRepository,
                         TaskRepository taskRepository,
                         UserRepository userRepository,
                         CreditService creditService,
                         NotificationService notificationService,
                         AuditLogService auditLogService,
                         UserContext userContext) {
        this.reviewRepository = reviewRepository;
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.creditService = creditService;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
        this.userContext = userContext;
    }

    /**
     * Submit a review for a completed task within the 24-hour evaluation window.
     */
    @Transactional
    public Review submitReview(Long taskId, Long reviewerId, ReviewSubmitRequest request) {
        // Validate task exists and is in COMPLETED status
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(404, "任务不存在"));

        if (task.getStatus() != TaskStatus.COMPLETED) {
            throw new BusinessException(400, "只有已完成的任务才能评价");
        }

        // Validate evaluation window: completedAt + 24h has NOT passed
        LocalDateTime completedAt = task.getCompletedAt();
        if (completedAt == null) {
            throw new BusinessException(400, "任务完成时间异常");
        }
        if (LocalDateTime.now().isAfter(completedAt.plusHours(24))) {
            throw new BusinessException(400, "评价窗口已关闭（已完成超过24小时）");
        }

        // Validate reviewer is either publisher or winner
        boolean isPublisher = task.getPublisherId().equals(reviewerId);
        boolean isWinner = task.getWinnerId() != null && task.getWinnerId().equals(reviewerId);
        if (!isPublisher && !isWinner) {
            throw new BusinessException(403, "只有任务发布者或中标者才能评价");
        }

        // Validate not already submitted a review for this task by this reviewer
        Optional<Review> existingReview = reviewRepository.findByTaskIdAndReviewerId(taskId, reviewerId);
        if (existingReview.isPresent()) {
            throw new BusinessException(400, "您已经提交过对此任务的评价");
        }

        // Determine review type and reviewee
        ReviewType reviewType;
        Long revieweeId;
        if (isPublisher) {
            reviewType = ReviewType.PUBLISHER_TO_WORKER;
            revieweeId = task.getWinnerId();
        } else {
            reviewType = ReviewType.WORKER_TO_PUBLISHER;
            revieweeId = task.getPublisherId();
        }

        if (revieweeId == null) {
            throw new BusinessException(400, "被评价方不存在");
        }

        // Create Review entity
        Review review = new Review();
        review.setTaskId(taskId);
        review.setReviewerId(reviewerId);
        review.setRevieweeId(revieweeId);
        review.setReviewType(reviewType);
        review.setStars(request.getStars());
        review.setComment(request.getComment());
        review.setIsDefault(false);
        review.setCreatedAt(LocalDateTime.now());
        review = reviewRepository.save(review);

        // Notify reviewee
        User reviewer = userRepository.findById(reviewerId).orElse(null);
        String reviewerName = reviewer != null ? reviewer.getNickname() : "用户";
        notificationService.createNotification(revieweeId,
                NotificationType.REVIEW_REQUEST,
                "收到新评价",
                reviewerName + " 对您进行了" + request.getStars() + "星评价",
                "/tasks/" + taskId);

        // Trigger async credit score recalculation for reviewee
        triggerCreditRecalculation(revieweeId);

        // Record audit log
        auditLogService.log(reviewerId, AuditActionType.REVIEW_SUBMIT,
                "review", review.getId(),
                "提交评价: taskId=" + taskId + ", stars=" + request.getStars(), "127.0.0.1");

        return review;
    }

    /**
     * Async trigger for credit score recalculation.
     */
    @Async
    public void triggerCreditRecalculation(Long userId) {
        try {
            creditService.recalculateCreditScore(userId);
        } catch (Exception e) {
            // Log silently - credit recalculation failure should not block review submission
            System.err.println("Credit recalculation failed for user " + userId + ": " + e.getMessage());
        }
    }

    /**
     * Get paginated reviews received by a user.
     */
    public Page<Review> getReviewsForUser(Long userId, Pageable pageable) {
        return reviewRepository.findByRevieweeIdOrderByCreatedAtDesc(userId, pageable);
    }

    /**
     * Get the review submitted by a specific user for a specific task.
     */
    public Review getReviewForTask(Long taskId, Long userId) {
        return reviewRepository.findByTaskIdAndReviewerId(taskId, userId)
                .orElseThrow(() -> new BusinessException(404, "未找到该评价"));
    }

    /**
     * Create default 5-star reviews when the 24-hour evaluation window expires.
     * Called by scheduler. Sets isDefault = true for auto-generated reviews.
     */
    @Transactional
    public void createDefaultReviews(Long taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(404, "任务不存在"));

        if (task.getStatus() != TaskStatus.COMPLETED) {
            return;
        }

        Long publisherId = task.getPublisherId();
        Long winnerId = task.getWinnerId();

        if (winnerId == null) {
            return;
        }

        // If publisher hasn't submitted review: create default 5-star PUBLISHER_TO_WORKER review
        Optional<Review> publisherReview = reviewRepository.findByTaskIdAndReviewerId(taskId, publisherId);
        if (!publisherReview.isPresent()) {
            Review defaultReview = new Review();
            defaultReview.setTaskId(taskId);
            defaultReview.setReviewerId(publisherId);
            defaultReview.setRevieweeId(winnerId);
            defaultReview.setReviewType(ReviewType.PUBLISHER_TO_WORKER);
            defaultReview.setStars(5);
            defaultReview.setComment("系统默认好评");
            defaultReview.setIsDefault(true);
            defaultReview.setCreatedAt(LocalDateTime.now());
            reviewRepository.save(defaultReview);
        }

        // If worker hasn't submitted review: create default 5-star WORKER_TO_PUBLISHER review
        Optional<Review> workerReview = reviewRepository.findByTaskIdAndReviewerId(taskId, winnerId);
        if (!workerReview.isPresent()) {
            Review defaultReview = new Review();
            defaultReview.setTaskId(taskId);
            defaultReview.setReviewerId(winnerId);
            defaultReview.setRevieweeId(publisherId);
            defaultReview.setReviewType(ReviewType.WORKER_TO_PUBLISHER);
            defaultReview.setStars(5);
            defaultReview.setComment("系统默认好评");
            defaultReview.setIsDefault(true);
            defaultReview.setCreatedAt(LocalDateTime.now());
            reviewRepository.save(defaultReview);
        }
    }

    /**
     * Find tasks where user is publisher or winner, status=COMPLETED,
     * and completedAt is within the last 24 hours, and no review has been submitted yet.
     * Returns list of taskIds that need a review.
     */
    public List<Long> getEligibleForReviewTasks(Long userId) {
        List<Long> eligibleTaskIds = new ArrayList<>();
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);

        // Find tasks where user is publisher
        List<Task> publisherTasks = taskRepository.findByPublisherId(userId);
        for (Task task : publisherTasks) {
            if (task.getStatus() == TaskStatus.COMPLETED
                    && task.getCompletedAt() != null
                    && task.getCompletedAt().isAfter(cutoff)) {
                // Check if user already reviewed
                if (reviewRepository.findByTaskIdAndReviewerId(task.getId(), userId).isEmpty()) {
                    eligibleTaskIds.add(task.getId());
                }
            }
        }

        // Find tasks where user is winner
        List<Task> winnerTasks = taskRepository.findByWinnerId(userId);
        for (Task task : winnerTasks) {
            if (task.getStatus() == TaskStatus.COMPLETED
                    && task.getCompletedAt() != null
                    && task.getCompletedAt().isAfter(cutoff)) {
                // Check if user already reviewed
                if (reviewRepository.findByTaskIdAndReviewerId(task.getId(), userId).isEmpty()) {
                    if (!eligibleTaskIds.contains(task.getId())) {
                        eligibleTaskIds.add(task.getId());
                    }
                }
            }
        }

        return eligibleTaskIds;
    }
}
