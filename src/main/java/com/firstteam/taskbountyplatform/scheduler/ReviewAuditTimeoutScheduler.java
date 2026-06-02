package com.firstteam.taskbountyplatform.scheduler;

import com.firstteam.taskbountyplatform.common.enums.AuditItemType;
import com.firstteam.taskbountyplatform.admin.entity.ReviewAudit;
import com.firstteam.taskbountyplatform.common.enums.ReviewAuditStatus;
import com.firstteam.taskbountyplatform.admin.repository.ReviewAuditRepository;
import com.firstteam.taskbountyplatform.common.enums.NotificationType;
import com.firstteam.taskbountyplatform.notification.service.NotificationService;
import com.firstteam.taskbountyplatform.user.entity.User;
import com.firstteam.taskbountyplatform.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Scheduler that handles review audit timeouts.
 * Automatically rejects audit applications that have been pending for more than 24 hours.
 */
@Component
public class ReviewAuditTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReviewAuditTimeoutScheduler.class);

    private static final int AUDIT_TIMEOUT_HOURS = 24;

    private final ReviewAuditRepository reviewAuditRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public ReviewAuditTimeoutScheduler(ReviewAuditRepository reviewAuditRepository,
                                       NotificationService notificationService,
                                       UserRepository userRepository) {
        this.reviewAuditRepository = reviewAuditRepository;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    /**
     * Every 10 minutes: check for audit applications that have been pending
     * for more than 24 hours and auto-reject them.
     */
    @Scheduled(cron = "0 */10 * * * ?")
    @Transactional
    public void checkReviewAuditTimeout() {
        log.info("Starting checkReviewAuditTimeout...");
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime timeoutCutoff = now.minusHours(AUDIT_TIMEOUT_HOURS);

        // Find all PENDING audits whose submittedAt + 24h < now
        // Fetch all pending audits in pages and filter by timeout
        List<ReviewAudit> expiredAudits = reviewAuditRepository
                .findByStatus(ReviewAuditStatus.PENDING, PageRequest.of(0, Integer.MAX_VALUE))
                .stream()
                .filter(audit -> audit.getSubmittedAt() != null
                        && audit.getSubmittedAt().isBefore(timeoutCutoff))
                .toList();

        if (expiredAudits.isEmpty()) {
            log.debug("No expired review audits found");
            return;
        }

        log.info("Found {} review audits that have timed out", expiredAudits.size());

        int autoRejectedCount = 0;
        for (ReviewAudit audit : expiredAudits) {
            try {
                autoRejectAudit(audit, now);
                autoRejectedCount++;
            } catch (Exception e) {
                log.error("Failed to auto-reject audit id={}: {}", audit.getId(), e.getMessage(), e);
            }
        }

        log.info("checkReviewAuditTimeout completed. Auto-rejected {} audits", autoRejectedCount);
    }

    /**
     * Auto-reject a timed-out audit application.
     * Sets status to TIMEOUT_REJECTED.
     * For NICKNAME audits: no cooldown penalty (user can resubmit immediately).
     * For other audit types: standard rejection handling.
     */
    @Transactional
    protected void autoRejectAudit(ReviewAudit audit, LocalDateTime now) {
        // Set status to TIMEOUT_REJECTED
        audit.setStatus(ReviewAuditStatus.TIMEOUT_REJECTED);
        audit.setProcessedAt(now);
        audit.setRejectReason("审核超时自动拒绝 - 申请提交超过 " + AUDIT_TIMEOUT_HOURS + " 小时未处理");

        reviewAuditRepository.save(audit);

        log.info("Auto-rejected audit id={}, applicantId={}, auditType={}, submittedAt={}",
                audit.getId(), audit.getApplicantId(), audit.getAuditType(),
                audit.getSubmittedAt() != null
                        ? audit.getSubmittedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        : "null");

        // Notify the applicant
        notifyApplicant(audit);

        // Notify admin
        notifyAdmin(audit);

        // Record admin log
        log.info("ReviewAudit id={} auto-rejected: type={}, applicant={}",
                audit.getId(), audit.getAuditType(), audit.getApplicantId());
    }

    /**
     * Send notification to the audit applicant about the timeout rejection.
     * For NICKNAME audits: explicitly note there's no cooldown penalty.
     */
    protected void notifyApplicant(ReviewAudit audit) {
        try {
            String auditTypeName = getAuditTypeDisplayName(audit.getAuditType());
            String title = auditTypeName + "审核超时自动拒绝";
            String content;

            if (audit.getAuditType() == AuditItemType.NICKNAME) {
                content = "您提交的" + auditTypeName + "修改申请因超过 "
                        + AUDIT_TIMEOUT_HOURS + " 小时未处理而被系统自动拒绝。"
                        + "昵称修改申请无冷静期限制，您可以重新提交。"
                        + "原申请内容：从「" + audit.getOldValue() + "」改为「" + audit.getNewValue() + "」";
            } else {
                content = "您提交的" + auditTypeName + "修改申请因超过 "
                        + AUDIT_TIMEOUT_HOURS + " 小时未处理而被系统自动拒绝。"
                        + "您可以重新提交申请。"
                        + "原申请内容：从「" + audit.getOldValue() + "」改为「" + audit.getNewValue() + "」";
            }

            notificationService.createNotification(
                    audit.getApplicantId(),
                    getNotificationTypeForAuditType(audit.getAuditType()),
                    title,
                    content,
                    "/user/profile"
            );
        } catch (Exception e) {
            log.warn("Failed to send timeout rejection notification to applicant id={} for audit id={}: {}",
                    audit.getApplicantId(), audit.getId(), e.getMessage());
        }
    }

    /**
     * Send a notification to admins about the timeout.
     * In production, this would notify all active admin users.
     */
    protected void notifyAdmin(ReviewAudit audit) {
        try {
            // Find all admin users
            List<User> admins = userRepository.findAll().stream()
                    .filter(u -> u.getRole() != null
                            && ("ADMIN".equalsIgnoreCase(u.getRole().name())
                            || "ADMIN".equals(u.getRole().name())))
                    .toList();

            if (admins.isEmpty()) {
                log.info("No admin users found to notify about audit timeout id={}", audit.getId());
                return;
            }

            String auditTypeName = getAuditTypeDisplayName(audit.getAuditType());
            String title = "审核超时提醒";
            String content = auditTypeName + "审核申请 #" + audit.getId()
                    + " 已超时 " + AUDIT_TIMEOUT_HOURS + " 小时未处理，系统已自动拒绝。"
                    + "申请人ID: " + audit.getApplicantId()
                    + "，提交时间: " + (audit.getSubmittedAt() != null
                    ? audit.getSubmittedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                    : "未知");

            for (User admin : admins) {
                try {
                    notificationService.createNotification(
                            admin.getId(),
                            NotificationType.SYSTEM_NOTICE,
                            title,
                            content,
                            "/admin/review-audits"
                    );
                } catch (Exception e) {
                    log.warn("Failed to send admin notification to admin id={} for audit id={}: {}",
                            admin.getId(), audit.getId(), e.getMessage());
                }
            }

            log.info("Notified {} admins about audit timeout id={}", admins.size(), audit.getId());
        } catch (Exception e) {
            log.warn("Failed to notify admins about audit timeout id={}: {}", audit.getId(), e.getMessage());
        }
    }

    /**
     * Get a human-readable display name for the audit item type.
     */
    protected String getAuditTypeDisplayName(AuditItemType auditType) {
        if (auditType == null) {
            return "未知类型";
        }
        return switch (auditType) {
            case NICKNAME -> "昵称";
            case AVATAR -> "头像";
            case ANNOUNCEMENT -> "公告栏";
            case EMAIL -> "邮箱";
            case PHONE -> "手机号";
        };
    }

    /**
     * Map audit item type to the appropriate notification type.
     */
    protected NotificationType getNotificationTypeForAuditType(AuditItemType auditType) {
        if (auditType == null) {
            return NotificationType.SYSTEM_NOTICE;
        }
        return switch (auditType) {
            case NICKNAME -> NotificationType.SYSTEM_NOTICE;
            case AVATAR -> NotificationType.SYSTEM_NOTICE;
            case ANNOUNCEMENT -> NotificationType.SYSTEM_NOTICE;
            case EMAIL, PHONE -> NotificationType.SYSTEM_NOTICE;
        };
    }
}
