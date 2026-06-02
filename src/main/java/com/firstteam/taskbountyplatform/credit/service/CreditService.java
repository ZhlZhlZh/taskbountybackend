package com.firstteam.taskbountyplatform.credit.service;

import com.firstteam.taskbountyplatform.common.enums.AuditActionType;
import com.firstteam.taskbountyplatform.audit.service.AuditLogService;
import com.firstteam.taskbountyplatform.auth.security.UserContext;
import com.firstteam.taskbountyplatform.common.enums.NotificationType;
import com.firstteam.taskbountyplatform.common.enums.CreditChangeReason;
import com.firstteam.taskbountyplatform.common.exception.BusinessException;
import com.firstteam.taskbountyplatform.config.PlatformConfig;
import com.firstteam.taskbountyplatform.credit.dto.CreditRuleUpdateRequest;
import com.firstteam.taskbountyplatform.credit.entity.CreditRecord;
import com.firstteam.taskbountyplatform.credit.entity.CreditRuleConfig;
import com.firstteam.taskbountyplatform.credit.repository.CreditRecordRepository;
import com.firstteam.taskbountyplatform.credit.repository.CreditRuleConfigRepository;
import com.firstteam.taskbountyplatform.notification.service.NotificationService;
import com.firstteam.taskbountyplatform.common.enums.ReviewType;
import com.firstteam.taskbountyplatform.review.repository.ReviewRepository;
import com.firstteam.taskbountyplatform.task.entity.Task;
import com.firstteam.taskbountyplatform.common.enums.TaskStatus;
import com.firstteam.taskbountyplatform.task.repository.TaskRepository;
import com.firstteam.taskbountyplatform.user.entity.User;
import com.firstteam.taskbountyplatform.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CreditService {

    private final CreditRecordRepository creditRecordRepository;
    private final CreditRuleConfigRepository creditRuleConfigRepository;
    private final UserRepository userRepository;
    private final ReviewRepository reviewRepository;
    private final TaskRepository taskRepository;
    private final PlatformConfig platformConfig;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final UserContext userContext;

    public CreditService(CreditRecordRepository creditRecordRepository,
                         CreditRuleConfigRepository creditRuleConfigRepository,
                         UserRepository userRepository,
                         ReviewRepository reviewRepository,
                         TaskRepository taskRepository,
                         PlatformConfig platformConfig,
                         NotificationService notificationService,
                         AuditLogService auditLogService,
                         UserContext userContext) {
        this.creditRecordRepository = creditRecordRepository;
        this.creditRuleConfigRepository = creditRuleConfigRepository;
        this.userRepository = userRepository;
        this.reviewRepository = reviewRepository;
        this.taskRepository = taskRepository;
        this.platformConfig = platformConfig;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
        this.userContext = userContext;
    }

    /**
     * Recalculate credit score for a user based on completion rate and praise rate.
     */
    @Transactional
    public void recalculateCreditScore(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));

        // Calculate completion rate: completed tasks / total accepted tasks
        List<Task> winnerTasks = taskRepository.findByWinnerId(userId);
        long completedCount = winnerTasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.COMPLETED).count();
        long totalAccepted = winnerTasks.isEmpty() ? 1 : winnerTasks.size();
        double completionRate = (double) completedCount / totalAccepted * 100;

        // Calculate praise rate: good reviews (4+ stars) / total reviews
        long goodReviews = reviewRepository.countGoodReviews(userId,
                ReviewType.WORKER_TO_PUBLISHER.name());
        long totalReviews = reviewRepository.countTotalReviews(userId,
                ReviewType.WORKER_TO_PUBLISHER.name());
        double praiseRate = totalReviews > 0
                ? (double) goodReviews / totalReviews * 100 : 100.0;

        // Apply rules from CreditRuleConfig with fallback to PlatformConfig defaults
        PlatformConfig.Credit creditCfg = platformConfig.getCredit();
        int scoreChange = 0;
        StringBuilder changeDesc = new StringBuilder("信用分重新计算");

        // Completion rate rules
        if (completionRate >= getRuleThreshold("completion_rate_good", creditCfg.getCompletionRateGood())) {
            int delta = getRuleDelta("completion_rate_good", creditCfg.getCompletionGoodScore());
            scoreChange += delta;
            changeDesc.append("; 完成率达标(").append(String.format("%.0f%%", completionRate))
                    .append(")+" + delta);
        } else if (completionRate < getRuleThreshold("completion_rate_bad", creditCfg.getCompletionRateBad())) {
            int delta = getRuleDelta("completion_rate_bad", creditCfg.getCompletionBadScore());
            scoreChange += delta;
            changeDesc.append("; 完成率过低(").append(String.format("%.0f%%", completionRate))
                    .append(")" + delta);
        }

        // Praise rate rules
        if (praiseRate >= getRuleThreshold("praise_rate_good", creditCfg.getPraiseRateGood())) {
            int delta = getRuleDelta("praise_rate_good", creditCfg.getPraiseGoodScore());
            scoreChange += delta;
            changeDesc.append("; 好评率达标(").append(String.format("%.0f%%", praiseRate))
                    .append(")+" + delta);
        } else if (praiseRate < getRuleThreshold("praise_rate_bad", creditCfg.getPraiseRateBad())) {
            int delta = getRuleDelta("praise_rate_bad", creditCfg.getPraiseBadScore());
            scoreChange += delta;
            changeDesc.append("; 好评率过低(").append(String.format("%.0f%%", praiseRate))
                    .append(")" + delta);
        }

        // Only record and update if there's a change
        if (scoreChange != 0) {
            int beforeScore = user.getCreditScore();
            int afterScore = clampScore(beforeScore + scoreChange);
            user.setCreditScore(afterScore);
            userRepository.save(user);

            // Record credit change
            CreditRecord record = new CreditRecord();
            record.setUserId(userId);
            record.setTaskId(null);
            record.setChangeScore(afterScore - beforeScore);
            record.setReasonType(CreditChangeReason.SYSTEM_ADJUST);
            record.setBeforeScore(beforeScore);
            record.setAfterScore(afterScore);
            record.setDescription(changeDesc.toString());
            record.setCreatedAt(LocalDateTime.now());
            creditRecordRepository.save(record);

            // Send notification
            notificationService.createNotification(userId,
                    NotificationType.CREDIT_CHANGE,
                    "信用分变更",
                    "您的信用分已从" + beforeScore + "调整为" + afterScore + "分。" + changeDesc,
                    null);
        }
    }

    /**
     * Add a credit record for a specific event and update user's credit score.
     */
    @Transactional
    public void addCreditRecord(Long userId, Long taskId, int changeScore,
                                CreditChangeReason reason, String description) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));

        int beforeScore = user.getCreditScore();
        int afterScore = clampScore(beforeScore + changeScore);
        user.setCreditScore(afterScore);
        userRepository.save(user);

        CreditRecord record = new CreditRecord();
        record.setUserId(userId);
        record.setTaskId(taskId);
        record.setChangeScore(changeScore);
        record.setReasonType(reason);
        record.setBeforeScore(beforeScore);
        record.setAfterScore(afterScore);
        record.setDescription(description);
        record.setCreatedAt(LocalDateTime.now());
        creditRecordRepository.save(record);

        // Notify user
        notificationService.createNotification(userId,
                NotificationType.CREDIT_CHANGE,
                "信用分变更",
                description != null ? description : "信用分变动" + (changeScore >= 0 ? "+" : "") + changeScore,
                null);
    }

    /**
     * Apply overtime penalty.
     */
    @Transactional
    public void penalizeForOvertime(Long userId, Long taskId) {
        int penalty = platformConfig.getCredit().getOvertimePenalty();
        addCreditRecord(userId, taskId, penalty,
                CreditChangeReason.OVERTIME,
                "任务超时未交付，扣" + Math.abs(penalty) + "分");
    }

    /**
     * Apply penalty for voluntary quit.
     */
    @Transactional
    public void penalizeForVoluntaryQuit(Long userId, Long taskId) {
        int penalty = platformConfig.getCredit().getVoluntaryQuitPenalty();
        addCreditRecord(userId, taskId, penalty,
                CreditChangeReason.VOLUNTARY_QUIT,
                "主动放弃任务，扣" + Math.abs(penalty) + "分");
    }

    /**
     * Apply penalty for publisher canceling a task without valid reason.
     */
    @Transactional
    public void penalizeForPublisherCancel(Long userId, Long taskId) {
        int penalty = platformConfig.getCredit().getPublisherCancelPenalty();
        addCreditRecord(userId, taskId, penalty,
                CreditChangeReason.PUBLISHER_CANCEL,
                "发布者无故下架任务，扣" + Math.abs(penalty) + "分");
    }

    /**
     * Check low credit score and return warning message.
     * Users with score < 40 cannot accept new orders.
     */
    public String checkAndWarnLowCredit(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));

        int score = user.getCreditScore();
        int warningThreshold = platformConfig.getCredit().getWarningThreshold();
        int restrictThreshold = platformConfig.getCredit().getRestrictThreshold();

        if (score < restrictThreshold) {
            return "您的信用分过低（" + score + "分），无法接受新订单。请联系管理员。";
        } else if (score < warningThreshold) {
            return "您的信用分偏低（" + score + "分），请注意保持良好行为，避免被限制接单。";
        }
        return null;
    }

    /**
     * Get paginated credit records for a user.
     */
    public Page<CreditRecord> getCreditRecords(Long userId, Pageable pageable) {
        return creditRecordRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    /**
     * Reset credit score to 80 (admin only, one-time use).
     */
    @Transactional
    public void resetCreditScore(Long userId, String reason) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));

        if (Boolean.TRUE.equals(user.getCreditResetUsed())) {
            throw new BusinessException(400, "该用户已经使用过信用重置");
        }

        int beforeScore = user.getCreditScore();
        user.setCreditScore(platformConfig.getCredit().getInitialScore());
        user.setCreditResetUsed(true);
        userRepository.save(user);

        // Record credit change
        CreditRecord record = new CreditRecord();
        record.setUserId(userId);
        record.setTaskId(null);
        record.setChangeScore(platformConfig.getCredit().getInitialScore() - beforeScore);
        record.setReasonType(CreditChangeReason.ADMIN_RESET);
        record.setBeforeScore(beforeScore);
        record.setAfterScore(platformConfig.getCredit().getInitialScore());
        record.setDescription("管理员重置信用分: " + (reason != null ? reason : "无说明"));
        record.setCreatedAt(LocalDateTime.now());
        creditRecordRepository.save(record);

        // Audit log
        Long operatorId = userContext.getCurrentUserId();
        auditLogService.log(operatorId, AuditActionType.ADMIN_CREDIT_RESET,
                "credit_reset", userId,
                "重置用户" + userId + "信用分从" + beforeScore + "到" +
                platformConfig.getCredit().getInitialScore() + "; 原因: " + reason, "127.0.0.1");

        // Notify
        notificationService.createNotification(userId,
                NotificationType.CREDIT_CHANGE,
                "信用分已重置",
                "管理员已将您的信用分重置为" + platformConfig.getCredit().getInitialScore() + "分",
                null);
    }

    /**
     * Update credit rule configs (admin only).
     */
    @Transactional
    public void updateCreditRules(List<CreditRuleUpdateRequest> rules) {
        for (CreditRuleUpdateRequest ruleUpdate : rules) {
            CreditRuleConfig config = creditRuleConfigRepository.findByRuleKey(ruleUpdate.getRuleKey())
                    .orElseGet(() -> {
                        CreditRuleConfig newConfig = new CreditRuleConfig();
                        newConfig.setRuleKey(ruleUpdate.getRuleKey());
                        newConfig.setRuleName(ruleUpdate.getRuleKey());
                        return newConfig;
                    });

            if (ruleUpdate.getThresholdValue() != null) {
                config.setThresholdValue(ruleUpdate.getThresholdValue());
            }
            if (ruleUpdate.getScoreDelta() != null) {
                config.setScoreDelta(ruleUpdate.getScoreDelta());
            }
            if (ruleUpdate.getEnabled() != null) {
                config.setEnabled(ruleUpdate.getEnabled());
            }
            config.setUpdatedAt(LocalDateTime.now());
            creditRuleConfigRepository.save(config);
        }

        Long operatorId = userContext.getCurrentUserId();
        auditLogService.log(operatorId, AuditActionType.ADMIN_UPDATE_CONFIG,
                "credit_rules", null,
                "更新了" + rules.size() + "条信用规则", "127.0.0.1");
    }

    /**
     * Get all credit rule configs.
     */
    public List<CreditRuleConfig> getCreditRules() {
        return creditRuleConfigRepository.findAll();
    }

    // ========== Private Helper Methods ==========

    private int clampScore(int score) {
        int min = platformConfig.getCredit().getMinScore();
        int max = platformConfig.getCredit().getMaxScore();
        return Math.max(min, Math.min(max, score));
    }

    private int getRuleThreshold(String ruleKey, int defaultValue) {
        return creditRuleConfigRepository.findByRuleKey(ruleKey)
                .filter(c -> Boolean.TRUE.equals(c.getEnabled()))
                .map(c -> {
                    try {
                        return Integer.parseInt(c.getThresholdValue());
                    } catch (NumberFormatException e) {
                        return defaultValue;
                    }
                })
                .orElse(defaultValue);
    }

    private int getRuleDelta(String ruleKey, int defaultValue) {
        return creditRuleConfigRepository.findByRuleKey(ruleKey)
                .filter(c -> Boolean.TRUE.equals(c.getEnabled()))
                .map(CreditRuleConfig::getScoreDelta)
                .orElse(defaultValue);
    }
}
