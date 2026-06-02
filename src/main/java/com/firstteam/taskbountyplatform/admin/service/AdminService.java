package com.firstteam.taskbountyplatform.admin.service;

import com.firstteam.taskbountyplatform.admin.dto.*;
import com.firstteam.taskbountyplatform.audit.service.AuditLogService;
import com.firstteam.taskbountyplatform.notification.service.NotificationService;
import com.firstteam.taskbountyplatform.admin.entity.ReviewAudit;
import com.firstteam.taskbountyplatform.common.enums.AuditItemType;
import com.firstteam.taskbountyplatform.common.enums.ReviewAuditStatus;
import com.firstteam.taskbountyplatform.admin.entity.SystemConfig;
import com.firstteam.taskbountyplatform.admin.repository.ReviewAuditRepository;
import com.firstteam.taskbountyplatform.admin.repository.SystemConfigRepository;
import com.firstteam.taskbountyplatform.audit.entity.AuditLog;
import com.firstteam.taskbountyplatform.auth.security.UserContext;
import com.firstteam.taskbountyplatform.common.enums.*;
import com.firstteam.taskbountyplatform.common.exception.BusinessException;
import com.firstteam.taskbountyplatform.common.response.PageResult;
import com.firstteam.taskbountyplatform.config.PlatformConfig;
import com.firstteam.taskbountyplatform.credit.entity.CreditRecord;
import com.firstteam.taskbountyplatform.credit.repository.CreditRecordRepository;
import com.firstteam.taskbountyplatform.notification.repository.NotificationRepository;
import com.firstteam.taskbountyplatform.point.entity.PointAccount;
import com.firstteam.taskbountyplatform.point.entity.PointFlow;
import com.firstteam.taskbountyplatform.point.repository.PointAccountRepository;
import com.firstteam.taskbountyplatform.point.repository.PointFlowRepository;
import com.firstteam.taskbountyplatform.report.entity.Report;
import com.firstteam.taskbountyplatform.report.repository.ReportRepository;
import com.firstteam.taskbountyplatform.review.repository.ReviewRepository;
import com.firstteam.taskbountyplatform.task.entity.Task;
import com.firstteam.taskbountyplatform.task.entity.TaskAppeal;
import com.firstteam.taskbountyplatform.task.entity.TaskCategory;
import com.firstteam.taskbountyplatform.task.repository.TaskAppealRepository;
import com.firstteam.taskbountyplatform.task.repository.TaskCategoryRepository;
import com.firstteam.taskbountyplatform.task.repository.TaskRepository;
import com.firstteam.taskbountyplatform.user.entity.User;
import com.firstteam.taskbountyplatform.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final TaskCategoryRepository taskCategoryRepository;
    private final PointAccountRepository pointAccountRepository;
    private final ReviewAuditRepository reviewAuditRepository;
    private final ReportRepository reportRepository;
    private final TaskAppealRepository taskAppealRepository;
    private final SystemConfigRepository systemConfigRepository;
    private final ReviewRepository reviewRepository;
    private final PointFlowRepository pointFlowRepository;
    private final CreditRecordRepository creditRecordRepository;
    private final NotificationRepository notificationRepository;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;
    private final UserContext userContext;
    private final PlatformConfig platformConfig;

    public AdminService(UserRepository userRepository,
                        TaskRepository taskRepository,
                        TaskCategoryRepository taskCategoryRepository,
                        PointAccountRepository pointAccountRepository,
                        ReviewAuditRepository reviewAuditRepository,
                        ReportRepository reportRepository,
                        TaskAppealRepository taskAppealRepository,
                        SystemConfigRepository systemConfigRepository,
                        ReviewRepository reviewRepository,
                        PointFlowRepository pointFlowRepository,
                        CreditRecordRepository creditRecordRepository,
                        NotificationRepository notificationRepository,
                        AuditLogService auditLogService,
                        NotificationService notificationService,
                        UserContext userContext,
                        PlatformConfig platformConfig) {
        this.userRepository = userRepository;
        this.taskRepository = taskRepository;
        this.taskCategoryRepository = taskCategoryRepository;
        this.pointAccountRepository = pointAccountRepository;
        this.reviewAuditRepository = reviewAuditRepository;
        this.reportRepository = reportRepository;
        this.taskAppealRepository = taskAppealRepository;
        this.systemConfigRepository = systemConfigRepository;
        this.reviewRepository = reviewRepository;
        this.pointFlowRepository = pointFlowRepository;
        this.creditRecordRepository = creditRecordRepository;
        this.notificationRepository = notificationRepository;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
        this.userContext = userContext;
        this.platformConfig = platformConfig;
    }

    // ==================== Dashboard ====================

    public AdminDashboardDTO getDashboard() {
        AdminDashboardDTO dto = new AdminDashboardDTO();

        // User stats
        dto.setTotalUsers(userRepository.count());
        LocalDateTime midnight = LocalDateTime.of(LocalDate.now(), LocalTime.MIDNIGHT);
        dto.setNewUsersToday(userRepository.countNewUsersSince(midnight));
        // Online users: those with lastLoginTime within last 15 minutes
        dto.setOnlineUsers(0); // estimated, requires real-time tracking

        // Task stats
        dto.setTotalTasks(taskRepository.count());
        dto.setInProgressTasks(taskRepository.countByStatus(TaskStatus.IN_PROGRESS));
        dto.setPendingConfirmTasks(taskRepository.countByStatus(TaskStatus.PENDING_CONFIRMATION));
        dto.setOverdueTasks(taskRepository.findOverdueTasks(LocalDateTime.now()).size());

        // Review audit stats
        com.firstteam.taskbountyplatform.common.enums.ReviewAuditStatus pendingStatus =
                com.firstteam.taskbountyplatform.common.enums.ReviewAuditStatus.PENDING;
        long pendingAuditCount = reviewAuditRepository.countByStatus(pendingStatus);
        dto.setPendingAvatarAudits(0);
        dto.setPendingNicknameAudits(0);
        dto.setPendingAnnouncementAudits(0);
        // Count by type from all pending audits
        List<ReviewAudit> pendingAudits = reviewAuditRepository.findByStatus(pendingStatus, Pageable.unpaged()).getContent();
        for (ReviewAudit audit : pendingAudits) {
            if (audit.getAuditType() == AuditItemType.AVATAR) {
                dto.setPendingAvatarAudits(dto.getPendingAvatarAudits() + 1);
            } else if (audit.getAuditType() == AuditItemType.NICKNAME) {
                dto.setPendingNicknameAudits(dto.getPendingNicknameAudits() + 1);
            } else if (audit.getAuditType() == AuditItemType.ANNOUNCEMENT) {
                dto.setPendingAnnouncementAudits(dto.getPendingAnnouncementAudits() + 1);
            }
        }

        // Appeal and Report stats
        dto.setPendingAppeals(taskAppealRepository.findByStatus(TaskAppeal.STATUS_PENDING).size());
        dto.setPendingReports(reportRepository.countByStatus(ReportStatus.PENDING));

        // Platform balance
        List<PointAccount> allAccounts = pointAccountRepository.findAll();
        int totalBalance = 0;
        for (PointAccount account : allAccounts) {
            totalBalance += (account.getAvailablePoints() != null ? account.getAvailablePoints() : 0);
            totalBalance += (account.getFrozenPoints() != null ? account.getFrozenPoints() : 0);
        }
        dto.setPlatformBalance(totalBalance);

        // Weekly fee (penalty deductions from last 7 days)
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        dto.setWeeklyFee(0); // computed from PointFlow records with flowType=EXPENSE and description containing "penalty"

        return dto;
    }

    // ==================== User Management ====================

    public PageResult<UserManagementDTO> listUsers(String keyword, String status,
                                                    Integer minScore, Integer maxScore,
                                                    Pageable pageable) {
        UserStatus userStatus = null;
        if (status != null && !status.isEmpty()) {
            try {
                userStatus = UserStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BusinessException("Invalid status: " + status);
            }
        }

        Page<User> userPage;
        if (keyword != null && !keyword.isEmpty()) {
            userPage = userRepository.searchByKeyword(keyword, pageable);
        } else if (userStatus != null || minScore != null || maxScore != null) {
            userPage = userRepository.filterUsers(userStatus, minScore, maxScore, pageable);
        } else {
            userPage = userRepository.findAll(pageable);
        }

        List<UserManagementDTO> dtos = userPage.getContent().stream()
                .map(this::toUserManagementDTO)
                .collect(Collectors.toList());

        return new PageResult<>(dtos, userPage.getNumber(), userPage.getSize(), userPage.getTotalElements());
    }

    public UserManagementDTO getUserDetail(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("User not found: " + userId));
        return toUserManagementDTO(user);
    }

    @Transactional
    public void freezeUser(Long userId, FreezeUserRequest request, Long adminId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("User not found: " + userId));

        if (user.getAccountStatus() == UserStatus.FROZEN) {
            throw new BusinessException("User is already frozen");
        }

        user.setAccountStatus(UserStatus.FROZEN);
        user.setFrozenUntil(LocalDateTime.now().plusDays(request.getFreezeDays()));
        user.setFreezeReason(request.getReason());
        userRepository.save(user);

        notificationService.createNotification(userId, NotificationType.FREEZE_NOTICE,
                "账户冻结通知",
                "您的账户已被冻结，原因：" + request.getReason() + "，解冻时间：" + user.getFrozenUntil(),
                null);

        auditLogService.log(adminId, AuditActionType.ADMIN_FREEZE_USER,
                "USER", userId,
                "Freeze user for " + request.getFreezeDays() + " days. Reason: " + request.getReason(),
                "0.0.0.0");
    }

    @Transactional
    public void unfreezeUser(Long userId, String reason, Long adminId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("User not found: " + userId));

        if (user.getAccountStatus() != UserStatus.FROZEN) {
            throw new BusinessException("User is not frozen");
        }

        user.setAccountStatus(UserStatus.NORMAL);
        user.setFrozenUntil(null);
        user.setFreezeReason(null);
        userRepository.save(user);

        notificationService.createNotification(userId, NotificationType.FREEZE_NOTICE,
                "账户解冻通知",
                "您的账户已被解冻。" + (reason != null ? " 原因：" + reason : ""),
                null);

        auditLogService.log(adminId, AuditActionType.ADMIN_UNFREEZE_USER,
                "USER", userId,
                "Unfreeze user. Reason: " + (reason != null ? reason : "N/A"),
                "0.0.0.0");
    }

    @Transactional
    public List<UserManagementDTO> handleGraduatedUsers(Long adminId) {
        List<User> graduatedUsers = userRepository.findByGraduatedTrueAndAccountStatus(UserStatus.NORMAL);
        List<UserManagementDTO> affectedUsers = new ArrayList<>();

        for (User user : graduatedUsers) {
            if (user.getGraduationFreezeCount() != null && user.getGraduationFreezeCount() < 2) {
                // User still has deferrals available, skip for now
                continue;
            }
            // Freeze the account
            user.setAccountStatus(UserStatus.FROZEN);
            user.setFrozenUntil(LocalDateTime.now().plusDays(30));
            user.setFreezeReason("Graduated user account frozen");
            userRepository.save(user);

            notificationService.createNotification(user.getId(), NotificationType.FREEZE_NOTICE,
                    "毕业生账户冻结",
                    "您的账户因毕业已被系统冻结。如有疑问请联系管理员。",
                    null);

            auditLogService.log(adminId, AuditActionType.SYSTEM_FREEZE_GRADUATED,
                    "USER", user.getId(),
                    "Graduated user account automatically frozen",
                    "0.0.0.0");

            affectedUsers.add(toUserManagementDTO(user));
        }

        return affectedUsers;
    }

    @Transactional
    public void deferGraduationFreeze(Long userId, int days, Long adminId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("User not found: " + userId));

        if (user.getGraduated() == null || !user.getGraduated()) {
            throw new BusinessException("User is not graduated");
        }

        int currentDeferCount = user.getGraduationFreezeCount() != null ? user.getGraduationFreezeCount() : 0;
        if (currentDeferCount >= 2) {
            throw new BusinessException("Graduation freeze can only be deferred up to 2 times");
        }
        if (days > 30) {
            throw new BusinessException("Each deferral cannot exceed 30 days");
        }

        user.setGraduationFreezeCount(currentDeferCount + 1);
        user.setFrozenUntil(LocalDateTime.now().plusDays(days));
        userRepository.save(user);

        auditLogService.log(adminId, AuditActionType.SYSTEM_FREEZE_GRADUATED,
                "USER", userId,
                "Graduation freeze deferred by " + days + " days, count=" + (currentDeferCount + 1),
                "0.0.0.0");

        notificationService.createNotification(userId, NotificationType.FREEZE_NOTICE,
                "冻结延期通知",
                "您的毕业生账户冻结已延期 " + days + " 天。",
                null);
    }

    @Transactional
    public void resetUserCredit(Long userId, AdminCreditResetRequest request, Long adminId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("User not found: " + userId));

        if (user.getCreditScore() == null || user.getCreditScore() >= 40) {
            throw new BusinessException("User credit score is not below 40, reset not applicable");
        }
        if (user.getCreditResetUsed() != null && user.getCreditResetUsed()) {
            throw new BusinessException("Credit reset has already been used for this user");
        }

        int beforeScore = user.getCreditScore();
        user.setCreditScore(80);
        user.setCreditResetUsed(true);
        userRepository.save(user);

        CreditRecord creditRecord = new CreditRecord();
        creditRecord.setUserId(userId);
        creditRecord.setChangeScore(80 - beforeScore);
        creditRecord.setReasonType(CreditChangeReason.ADMIN_RESET);
        creditRecord.setBeforeScore(beforeScore);
        creditRecord.setAfterScore(80);
        creditRecord.setDescription(request.getReason() != null ? request.getReason() : "Admin manual reset");
        creditRecord.setCreatedAt(LocalDateTime.now());
        creditRecordRepository.save(creditRecord);

        auditLogService.log(adminId, AuditActionType.ADMIN_CREDIT_RESET,
                "USER", userId,
                "Credit reset from " + beforeScore + " to 80. Reason: " + request.getReason(),
                "0.0.0.0");

        notificationService.createNotification(userId, NotificationType.CREDIT_CHANGE,
                "信用分重置",
                "您的信用分已由管理员重置为80分。",
                null);
    }

    public PageResult<AuditLog> getUserAuditLogs(Long userId, Pageable pageable) {
        Page<AuditLog> logs = auditLogService.getAuditLogs(userId, pageable);
        return new PageResult<>(logs.getContent(), logs.getNumber(), logs.getSize(), logs.getTotalElements());
    }

    // ==================== Task Management ====================

    public PageResult<Task> listAllTasks(String status, String keyword, Pageable pageable) {
        Page<Task> taskPage;
        if (status != null && !status.isEmpty() && keyword != null && !keyword.isEmpty()) {
            taskPage = taskRepository.findAll(
                    (root, query, cb) -> {
                        var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
                        try {
                            TaskStatus taskStatus = TaskStatus.valueOf(status.toUpperCase());
                            predicates.add(cb.equal(root.get("status"), taskStatus));
                        } catch (IllegalArgumentException ignored) {
                        }
                        predicates.add(cb.or(
                                cb.like(root.get("title"), "%" + keyword + "%"),
                                cb.like(root.get("description"), "%" + keyword + "%")
                        ));
                        return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
                    }, pageable);
        } else if (status != null && !status.isEmpty()) {
            try {
                TaskStatus taskStatus = TaskStatus.valueOf(status.toUpperCase());
                taskPage = taskRepository.findAll(
                        (root, query, cb) -> cb.equal(root.get("status"), taskStatus),
                        pageable);
            } catch (IllegalArgumentException e) {
                taskPage = taskRepository.findAll(pageable);
            }
        } else if (keyword != null && !keyword.isEmpty()) {
            taskPage = taskRepository.findAll(
                    (root, query, cb) -> cb.or(
                            cb.like(root.get("title"), "%" + keyword + "%"),
                            cb.like(root.get("description"), "%" + keyword + "%")
                    ), pageable);
        } else {
            taskPage = taskRepository.findAll(pageable);
        }

        return new PageResult<>(taskPage.getContent(), taskPage.getNumber(), taskPage.getSize(), taskPage.getTotalElements());
    }

    @Transactional
    public void forceCancelTask(Long taskId, String reason, Long adminId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException("Task not found: " + taskId));

        if (task.getStatus() == TaskStatus.COMPLETED || task.getStatus() == TaskStatus.CANCELLED) {
            throw new BusinessException("Cannot cancel a completed or already cancelled task");
        }

        task.setStatus(TaskStatus.CANCELLED);
        task.setCancelledAt(LocalDateTime.now());
        taskRepository.save(task);

        // Confiscate reward points: deduct from publisher's available balance
        PointAccount publisherAccount = pointAccountRepository.findByUserId(task.getPublisherId())
                .orElse(null);
        if (publisherAccount != null) {
            int confiscated = Math.min(publisherAccount.getAvailablePoints(), task.getRewardPoints());
            publisherAccount.setAvailablePoints(publisherAccount.getAvailablePoints() - confiscated);
            publisherAccount.setTotalExpense(publisherAccount.getTotalExpense() + confiscated);
            pointAccountRepository.save(publisherAccount);

            PointFlow flow = new PointFlow();
            flow.setUserId(task.getPublisherId());
            flow.setTaskId(taskId);
            flow.setChangeAmount(-confiscated);
            flow.setBalanceBefore(publisherAccount.getAvailablePoints() + confiscated);
            flow.setBalanceAfter(publisherAccount.getAvailablePoints());
            flow.setFlowType(PointFlowType.EXPENSE);
            flow.setDescription("Force cancelled task " + taskId + ", points confiscated: " + confiscated);
            flow.setCreatedAt(LocalDateTime.now());
            pointFlowRepository.save(flow);
        }

        auditLogService.log(adminId, AuditActionType.ADMIN_FORCE_CANCEL_TASK,
                "TASK", taskId,
                "Force cancel task. Reason: " + reason,
                "0.0.0.0");

        // Notify publisher
        notificationService.createNotification(task.getPublisherId(), NotificationType.TASK_CANCELLED,
                "任务被强制取消",
                "您的任务「" + task.getTitle() + "」已被管理员强制取消。原因：" + reason,
                null);

        // Notify worker if assigned
        if (task.getWinnerId() != null) {
            notificationService.createNotification(task.getWinnerId(), NotificationType.TASK_CANCELLED,
                    "任务被取消",
                    "您承接的任务「" + task.getTitle() + "」已被管理员强制取消。原因：" + reason,
                    null);
        }
    }

    @Transactional
    public void migrateTaskCategory(Long categoryId, Long targetCategoryId, List<Long> taskIds, Long adminId) {
        TaskCategory targetCategory = taskCategoryRepository.findById(targetCategoryId)
                .orElseThrow(() -> new BusinessException("Target category not found: " + targetCategoryId));

        int migratedCount = 0;
        for (Long taskId : taskIds) {
            Task task = taskRepository.findById(taskId).orElse(null);
            if (task == null) {
                continue;
            }
            if (task.getStatus() == TaskStatus.PUBLISHED || task.getStatus() == TaskStatus.IN_PROGRESS) {
                if (task.getCategoryId() != null && task.getCategoryId().equals(categoryId)) {
                    task.setCategoryId(targetCategoryId);
                    task.setCategoryName(targetCategory.getName());
                    taskRepository.save(task);
                    migratedCount++;
                }
            }
        }

        auditLogService.log(adminId, AuditActionType.ADMIN_MIGRATE_CATEGORY,
                "CATEGORY", categoryId,
                "Migrated " + migratedCount + " tasks from category " + categoryId + " to " + targetCategoryId,
                "0.0.0.0");
    }

    // ==================== Review Audit Management ====================

    public PageResult<ReviewAuditDTO> listReviewAudits(String type, String status, Pageable pageable) {
        Page<ReviewAudit> auditPage;

        com.firstteam.taskbountyplatform.common.enums.ReviewAuditStatus auditStatus = null;
        if (status != null && !status.isEmpty()) {
            try {
                auditStatus = com.firstteam.taskbountyplatform.common.enums.ReviewAuditStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BusinessException("Invalid audit status: " + status);
            }
        }
        final com.firstteam.taskbountyplatform.common.enums.ReviewAuditStatus finalAuditStatus = auditStatus;

        if (type != null && !type.isEmpty()) {
            auditPage = reviewAuditRepository.findByAuditType(type, pageable);
        } else if (finalAuditStatus != null) {
            auditPage = reviewAuditRepository.findByStatus(finalAuditStatus, pageable);
        } else {
            auditPage = reviewAuditRepository.findAll(pageable);
        }

        // Filter by status if both type and status are specified
        if (type != null && !type.isEmpty() && finalAuditStatus != null) {
            List<ReviewAudit> filtered = auditPage.getContent().stream()
                    .filter(a -> a.getStatus().name().equals(finalAuditStatus.name()))
                    .collect(Collectors.toList());
            auditPage = new org.springframework.data.domain.PageImpl<>(filtered, pageable, filtered.size());
        }

        List<ReviewAuditDTO> dtos = auditPage.getContent().stream()
                .map(this::toReviewAuditDTO)
                .collect(Collectors.toList());

        return new PageResult<>(dtos, auditPage.getNumber(), auditPage.getSize(), auditPage.getTotalElements());
    }

    @Transactional
    public void approveReviewAudit(Long auditId, Long adminId) {
        ReviewAudit audit = reviewAuditRepository.findById(auditId)
                .orElseThrow(() -> new BusinessException("Review audit not found: " + auditId));

        if (audit.getStatus() != ReviewAuditStatus.PENDING) {
            throw new BusinessException("Audit is not in PENDING status");
        }

        User user = userRepository.findById(audit.getApplicantId())
                .orElseThrow(() -> new BusinessException("User not found: " + audit.getApplicantId()));

        AuditActionType actionType;
        NotificationType notificationType;
        String notificationTitle;

        switch (audit.getAuditType()) {
            case NICKNAME:
                user.setNickname(audit.getNewValue());
                actionType = AuditActionType.ADMIN_APPROVE_NICKNAME;
                notificationType = NotificationType.NICKNAME_APPROVED;
                notificationTitle = "昵称审核通过";
                break;
            case AVATAR:
                user.setAvatarUrl(audit.getNewValue());
                actionType = AuditActionType.ADMIN_APPROVE_AVATAR;
                notificationType = NotificationType.AVATAR_APPROVED;
                notificationTitle = "头像审核通过";
                break;
            case ANNOUNCEMENT:
                user.setAnnouncement(audit.getNewValue());
                actionType = AuditActionType.ADMIN_APPROVE_ANNOUNCEMENT;
                notificationType = NotificationType.ANNOUNCEMENT_APPROVED;
                notificationTitle = "公告审核通过";
                break;
            default:
                throw new BusinessException("Unsupported audit type: " + audit.getAuditType());
        }

        userRepository.save(user);

        audit.setStatus(ReviewAuditStatus.APPROVED);
        audit.setAdminId(adminId);
        audit.setProcessedAt(LocalDateTime.now());
        reviewAuditRepository.save(audit);

        notificationService.createNotification(user.getId(), notificationType,
                notificationTitle,
                "您的" + audit.getAuditType().name() + "审核已通过。",
                null);

        auditLogService.log(adminId, actionType,
                "REVIEW_AUDIT", auditId,
                "Approved " + audit.getAuditType() + " audit for user " + user.getId(),
                "0.0.0.0");
    }

    @Transactional
    public void rejectReviewAudit(Long auditId, String reason, Long adminId) {
        ReviewAudit audit = reviewAuditRepository.findById(auditId)
                .orElseThrow(() -> new BusinessException("Review audit not found: " + auditId));

        if (audit.getStatus() != ReviewAuditStatus.PENDING) {
            throw new BusinessException("Audit is not in PENDING status");
        }

        audit.setStatus(ReviewAuditStatus.REJECTED);
        audit.setAdminId(adminId);
        audit.setRejectReason(reason);
        audit.setProcessedAt(LocalDateTime.now());
        reviewAuditRepository.save(audit);

        AuditActionType actionType;
        NotificationType notificationType;
        String notificationTitle;

        switch (audit.getAuditType()) {
            case NICKNAME:
                actionType = AuditActionType.ADMIN_REJECT_NICKNAME;
                notificationType = NotificationType.NICKNAME_REJECTED;
                notificationTitle = "昵称审核被拒";
                break;
            case AVATAR:
                actionType = AuditActionType.ADMIN_REJECT_AVATAR;
                notificationType = NotificationType.AVATAR_REJECTED;
                notificationTitle = "头像审核被拒";
                break;
            case ANNOUNCEMENT:
                actionType = AuditActionType.ADMIN_REJECT_ANNOUNCEMENT;
                notificationType = NotificationType.ANNOUNCEMENT_REJECTED;
                notificationTitle = "公告审核被拒";
                break;
            default:
                actionType = AuditActionType.ADMIN_REJECT_NICKNAME;
                notificationType = NotificationType.NICKNAME_REJECTED;
                notificationTitle = "审核被拒";
        }

        notificationService.createNotification(audit.getApplicantId(), notificationType,
                notificationTitle,
                "您的" + audit.getAuditType().name() + "审核已被拒绝。原因：" + reason,
                null);

        auditLogService.log(adminId, actionType,
                "REVIEW_AUDIT", auditId,
                "Rejected " + audit.getAuditType() + " audit for user " + audit.getApplicantId() + ". Reason: " + reason,
                "0.0.0.0");
    }

    @Transactional
    public void checkTimeoutAudits() {
        com.firstteam.taskbountyplatform.common.enums.ReviewAuditStatus pending =
                com.firstteam.taskbountyplatform.common.enums.ReviewAuditStatus.PENDING;
        List<ReviewAudit> pendingAudits = reviewAuditRepository.findByStatus(pending, Pageable.unpaged()).getContent();

        LocalDateTime now = LocalDateTime.now();
        int timeoutCount = 0;

        for (ReviewAudit audit : pendingAudits) {
            if (audit.getTimeoutAt() != null && audit.getTimeoutAt().isBefore(now)) {
                audit.setStatus(ReviewAuditStatus.TIMEOUT_REJECTED);
                audit.setRejectReason("Auto-rejected: audit timed out after 24 hours");
                audit.setProcessedAt(now);
                reviewAuditRepository.save(audit);

                notificationService.createNotification(audit.getApplicantId(),
                        audit.getAuditType() == AuditItemType.NICKNAME ? NotificationType.NICKNAME_REJECTED :
                        audit.getAuditType() == AuditItemType.AVATAR ? NotificationType.AVATAR_REJECTED :
                        NotificationType.ANNOUNCEMENT_REJECTED,
                        "审核超时自动拒绝",
                        "您的" + audit.getAuditType().name() + "审核因超时（24小时）被自动拒绝。",
                        null);

                timeoutCount++;
            }
        }

        if (timeoutCount > 0) {
            System.out.println("Auto-rejected " + timeoutCount + " timed-out review audits at " + now);
        }
    }

    // ==================== System Config ====================

    public List<SystemConfigDTO> getAllConfigs() {
        return systemConfigRepository.findAll().stream()
                .map(this::toSystemConfigDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public SystemConfigDTO updateConfig(Long configId, String value, Long adminId) {
        SystemConfig config = systemConfigRepository.findById(configId)
                .orElseThrow(() -> new BusinessException("Config not found: " + configId));

        String oldValue = config.getConfigValue();
        config.setConfigValue(value);
        config.setUpdatedBy(adminId);
        config.setUpdatedAt(LocalDateTime.now());
        systemConfigRepository.save(config);

        auditLogService.log(adminId, AuditActionType.ADMIN_UPDATE_CONFIG,
                "SYSTEM_CONFIG", configId,
                "Updated config " + config.getConfigKey() + " from '" + oldValue + "' to '" + value + "'",
                "0.0.0.0");

        return toSystemConfigDTO(config);
    }

    public SystemConfigDTO getConfig(String key) {
        SystemConfig config = systemConfigRepository.findByConfigKey(key)
                .orElseThrow(() -> new BusinessException("Config not found: " + key));
        return toSystemConfigDTO(config);
    }

    // ==================== Message Broadcast ====================

    public void sendBroadcast(BroadcastRequest request, Long adminId) {
        List<Long> targetUserIds = resolveTargetUsers(request.getTargetType(), request.getTargetUserIds());

        if (targetUserIds.isEmpty()) {
            throw new BusinessException("No target users found for broadcast");
        }

        notificationService.createBatchNotifications(targetUserIds, NotificationType.SYSTEM_BROADCAST, request.getTitle(), request.getContent(), null);

        auditLogService.log(adminId, AuditActionType.ADMIN_BROADCAST,
                "BROADCAST", null,
                "Sent broadcast to " + targetUserIds.size() + " users: " + request.getTitle(),
                "0.0.0.0");
    }

    public void createScheduledBroadcast(BroadcastRequest request, String cronExpression, Long adminId) {
        SystemConfig scheduledConfig = new SystemConfig();
        scheduledConfig.setConfigKey("scheduled_broadcast_" + System.currentTimeMillis());
        scheduledConfig.setConfigValue(request.getTitle() + "|||" + request.getContent() + "|||" +
                request.getTargetType() + "|||" + cronExpression);
        scheduledConfig.setConfigType("BROADCAST");
        scheduledConfig.setDescription("Scheduled broadcast: " + request.getTitle());
        scheduledConfig.setUpdatedBy(adminId);
        scheduledConfig.setUpdatedAt(LocalDateTime.now());
        systemConfigRepository.save(scheduledConfig);

        auditLogService.log(adminId, AuditActionType.ADMIN_BROADCAST,
                "SCHEDULED_BROADCAST", scheduledConfig.getId(),
                "Created scheduled broadcast: " + request.getTitle() + " with cron: " + cronExpression,
                "0.0.0.0");
    }

    private List<Long> resolveTargetUsers(String targetType, List<Long> specificIds) {
        if (targetType == null) {
            return Collections.emptyList();
        }

        switch (targetType.toUpperCase()) {
            case "ALL":
                return userRepository.findAll().stream()
                        .map(User::getId)
                        .collect(Collectors.toList());
            case "PUBLISHERS":
                return taskRepository.findAll().stream()
                        .map(Task::getPublisherId)
                        .distinct()
                        .collect(Collectors.toList());
            case "WORKERS":
                return taskRepository.findAll().stream()
                        .filter(t -> t.getWinnerId() != null)
                        .map(Task::getWinnerId)
                        .distinct()
                        .collect(Collectors.toList());
            case "SPECIFIC":
                return specificIds != null ? specificIds : Collections.emptyList();
            default:
                return Collections.emptyList();
        }
    }

    // ==================== Category Management ====================

    @Transactional
    public TaskCategory createCategory(String name, Integer sortOrder) {
        if (name == null || name.length() > 10) {
            throw new BusinessException("Category name must be 1-10 characters");
        }
        if (taskCategoryRepository.existsByName(name)) {
            throw new BusinessException("Category name already exists: " + name);
        }

        TaskCategory category = new TaskCategory();
        category.setName(name);
        category.setSortOrder(sortOrder != null ? sortOrder : 0);
        category.setEnabled(true);
        category.setCreatedAt(LocalDateTime.now());
        return taskCategoryRepository.save(category);
    }

    @Transactional
    public TaskCategory updateCategory(Long categoryId, String name, Integer sortOrder, Boolean enabled) {
        TaskCategory category = taskCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException("Category not found: " + categoryId));

        if (name != null) {
            if (name.length() > 10) {
                throw new BusinessException("Category name must be 1-10 characters");
            }
            if (!name.equals(category.getName()) && taskCategoryRepository.existsByName(name)) {
                throw new BusinessException("Category name already exists: " + name);
            }
            category.setName(name);
        }

        if (sortOrder != null) {
            category.setSortOrder(sortOrder);
        }

        if (enabled != null) {
            if (!enabled) {
                long inProgressCount = taskRepository.countByCategoryIdAndStatus(categoryId, TaskStatus.IN_PROGRESS);
                if (inProgressCount > 0) {
                    throw new BusinessException("Cannot disable category with " + inProgressCount + " in-progress tasks");
                }
            }
            category.setEnabled(enabled);
        }

        return taskCategoryRepository.save(category);
    }

    @Transactional
    public void deleteCategory(Long categoryId, Long adminId) {
        TaskCategory category = taskCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException("Category not found: " + categoryId));

        long publishedCount = taskRepository.countByCategoryIdAndStatus(categoryId, TaskStatus.PUBLISHED);
        long inProgressCount = taskRepository.countByCategoryIdAndStatus(categoryId, TaskStatus.IN_PROGRESS);

        if (publishedCount > 0 || inProgressCount > 0) {
            throw new BusinessException(
                    "Cannot delete category with active tasks. " +
                    publishedCount + " published, " + inProgressCount + " in-progress. " +
                    "Please migrate tasks first.");
        }

        taskCategoryRepository.delete(category);

        auditLogService.log(adminId, AuditActionType.ADMIN_MIGRATE_CATEGORY,
                "CATEGORY", categoryId,
                "Deleted category: " + category.getName(),
                "0.0.0.0");
    }

    // ==================== Appeal Management ====================

    public PageResult<TaskAppeal> listAppeals(String status, Pageable pageable) {
        Page<TaskAppeal> appealPage;
        if (status != null && !status.isEmpty()) {
            appealPage = taskAppealRepository.findByStatus(status, pageable);
        } else {
            appealPage = taskAppealRepository.findByStatus(TaskAppeal.STATUS_PENDING, pageable);
        }
        return new PageResult<>(appealPage.getContent(), appealPage.getNumber(),
                appealPage.getSize(), appealPage.getTotalElements());
    }

    public TaskAppeal getAppealDetail(Long appealId) {
        return taskAppealRepository.findById(appealId)
                .orElseThrow(() -> new BusinessException("Appeal not found: " + appealId));
    }

    @Transactional
    public TaskAppeal processAppeal(Long appealId, AppealProcessRequest request, Long adminId) {
        TaskAppeal appeal = taskAppealRepository.findById(appealId)
                .orElseThrow(() -> new BusinessException("Appeal not found: " + appealId));

        if (!TaskAppeal.STATUS_PENDING.equals(appeal.getStatus())) {
            throw new BusinessException("Appeal is not in PENDING status");
        }

        appeal.setStatus(TaskAppeal.STATUS_RESOLVED);
        appeal.setAdminId(adminId);
        appeal.setAdminDecision(request.getDecision());
        appeal.setAdminNote(request.getNote());
        appeal.setResolvedAt(LocalDateTime.now());
        taskAppealRepository.save(appeal);

        // Update task status based on decision
        Task task = taskRepository.findById(appeal.getTaskId()).orElse(null);
        if (task != null) {
            if (TaskAppeal.DECISION_COMPLETED.equals(request.getDecision())) {
                task.setStatus(TaskStatus.COMPLETED);
                task.setCompletedAt(LocalDateTime.now());
            } else if (TaskAppeal.DECISION_CANCELLED.equals(request.getDecision())) {
                task.setStatus(TaskStatus.CANCELLED);
                task.setCancelledAt(LocalDateTime.now());
            } else if (TaskAppeal.DECISION_IN_PROGRESS.equals(request.getDecision())) {
                task.setStatus(TaskStatus.IN_PROGRESS);
            }
            taskRepository.save(task);
        }

        notificationService.createNotification(appeal.getAppealerId(), NotificationType.APPEAL_RESULT,
                "申诉处理结果",
                "您的申诉已被处理。决定：" + request.getDecision() +
                (request.getNote() != null ? "，备注：" + request.getNote() : ""),
                null);

        auditLogService.log(adminId, AuditActionType.ADMIN_RESOLVE_APPEAL,
                "APPEAL", appealId,
                "Processed appeal: decision=" + request.getDecision() + ", note=" + request.getNote(),
                "0.0.0.0");

        return appeal;
    }

    // ==================== Report Management ====================

    public PageResult<Report> listReports(String status, Pageable pageable) {
        Page<Report> reportPage;
        if (status != null && !status.isEmpty()) {
            try {
                ReportStatus reportStatus = ReportStatus.valueOf(status.toUpperCase());
                reportPage = reportRepository.findByStatus(reportStatus, pageable);
            } catch (IllegalArgumentException e) {
                reportPage = reportRepository.findByStatus(ReportStatus.PENDING, pageable);
            }
        } else {
            reportPage = reportRepository.findByStatus(ReportStatus.PENDING, pageable);
        }
        return new PageResult<>(reportPage.getContent(), reportPage.getNumber(),
                reportPage.getSize(), reportPage.getTotalElements());
    }

    @Transactional
    public void approveReport(Long reportId, Long adminId, String adminNote,
                              Integer penaltyDays, Integer creditPenalty) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException("Report not found: " + reportId));

        if (report.getStatus() != ReportStatus.PENDING) {
            throw new BusinessException("Report is not in PENDING status");
        }

        report.setStatus(ReportStatus.APPROVED);
        report.setAdminId(adminId);
        report.setAdminNote(adminNote);
        report.setPenaltyDays(penaltyDays);
        report.setCreditPenalty(creditPenalty);
        report.setProcessedAt(LocalDateTime.now());
        reportRepository.save(report);

        // Apply credit penalty to the reported target (if target is a user)
        if (creditPenalty != null && creditPenalty != 0 && report.getTargetType() == ReportTargetType.USER) {
            User targetUser = userRepository.findById(report.getTargetId()).orElse(null);
            if (targetUser != null) {
                int beforeScore = targetUser.getCreditScore();
                int afterScore = Math.max(0, beforeScore + creditPenalty); // creditPenalty is negative
                targetUser.setCreditScore(afterScore);
                userRepository.save(targetUser);

                CreditRecord creditRecord = new CreditRecord();
                creditRecord.setUserId(report.getTargetId());
                creditRecord.setChangeScore(creditPenalty);
                creditRecord.setReasonType(CreditChangeReason.REPORT_PENALTY);
                creditRecord.setBeforeScore(beforeScore);
                creditRecord.setAfterScore(afterScore);
                creditRecord.setDescription("Report penalty from report " + reportId);
                creditRecord.setCreatedAt(LocalDateTime.now());
                creditRecordRepository.save(creditRecord);

                notificationService.createNotification(report.getTargetId(), NotificationType.CREDIT_CHANGE,
                        "信用分变更",
                        "您的信用分因举报被扣除 " + Math.abs(creditPenalty) + " 分。",
                        null);
            }
        }

        // Notify reporter
        notificationService.createNotification(report.getReporterId(), NotificationType.REPORT_RESULT,
                "举报处理结果",
                "您的举报已被核实。感谢您的贡献。",
                null);

        auditLogService.log(adminId, AuditActionType.ADMIN_APPROVE_REPORT,
                "REPORT", reportId,
                "Approved report. Penalty: credit=" + creditPenalty + ", days=" + penaltyDays,
                "0.0.0.0");
    }

    @Transactional
    public void rejectReport(Long reportId, Long adminId, String adminNote) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException("Report not found: " + reportId));

        if (report.getStatus() != ReportStatus.PENDING) {
            throw new BusinessException("Report is not in PENDING status");
        }

        report.setStatus(ReportStatus.REJECTED);
        report.setAdminId(adminId);
        report.setAdminNote(adminNote);
        report.setProcessedAt(LocalDateTime.now());
        reportRepository.save(report);

        notificationService.createNotification(report.getReporterId(), NotificationType.REPORT_RESULT,
                "举报处理结果",
                "您的举报已被驳回。" + (adminNote != null ? " 原因：" + adminNote : ""),
                null);

        auditLogService.log(adminId, AuditActionType.ADMIN_REJECT_REPORT,
                "REPORT", reportId,
                "Rejected report. Note: " + adminNote,
                "0.0.0.0");
    }

    @Transactional
    public void rejectReportWithPenalty(Long reportId, Long adminId, String adminNote,
                                        Integer penaltyDays, Integer creditPenalty) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException("Report not found: " + reportId));

        if (report.getStatus() != ReportStatus.PENDING) {
            throw new BusinessException("Report is not in PENDING status");
        }

        report.setStatus(ReportStatus.REJECTED);
        report.setAdminId(adminId);
        report.setAdminNote(adminNote);
        report.setPenaltyDays(penaltyDays);
        report.setCreditPenalty(creditPenalty);
        report.setProcessedAt(LocalDateTime.now());
        reportRepository.save(report);

        // Apply credit penalty to the reporter for false report
        if (creditPenalty != null && creditPenalty != 0) {
            User reporter = userRepository.findById(report.getReporterId()).orElse(null);
            if (reporter != null) {
                int beforeScore = reporter.getCreditScore();
                int afterScore = Math.max(0, beforeScore + creditPenalty);
                reporter.setCreditScore(afterScore);
                userRepository.save(reporter);

                CreditRecord creditRecord = new CreditRecord();
                creditRecord.setUserId(report.getReporterId());
                creditRecord.setChangeScore(creditPenalty);
                creditRecord.setReasonType(CreditChangeReason.FALSE_REPORT);
                creditRecord.setBeforeScore(beforeScore);
                creditRecord.setAfterScore(afterScore);
                creditRecord.setDescription("False report penalty from report " + reportId);
                creditRecord.setCreatedAt(LocalDateTime.now());
                creditRecordRepository.save(creditRecord);

                notificationService.createNotification(report.getReporterId(), NotificationType.CREDIT_CHANGE,
                        "信用分变更",
                        "您的信用分因不实举报被扣除 " + Math.abs(creditPenalty) + " 分。",
                        null);
            }
        }

        notificationService.createNotification(report.getReporterId(), NotificationType.REPORT_RESULT,
                "举报处理结果",
                "您的举报已被驳回并受到处罚。" + (adminNote != null ? " 原因：" + adminNote : ""),
                null);

        auditLogService.log(adminId, AuditActionType.ADMIN_REJECT_REPORT,
                "REPORT", reportId,
                "Rejected report with penalty. Penalty: credit=" + creditPenalty + ", days=" + penaltyDays,
                "0.0.0.0");
    }

    // ==================== Audit Logs ====================

    public PageResult<AuditLog> getAuditLogs(Pageable pageable) {
        Page<AuditLog> logPage = auditLogService.getAuditLogs(null, pageable);
        return new PageResult<>(logPage.getContent(), logPage.getNumber(), logPage.getSize(), logPage.getTotalElements());
    }

    // ==================== DTO Converters ====================

    private UserManagementDTO toUserManagementDTO(User user) {
        UserManagementDTO dto = new UserManagementDTO();
        dto.setId(user.getId());
        dto.setStudentNo(user.getStudentNo());
        dto.setRealName(user.getRealName());
        dto.setNickname(user.getNickname());
        dto.setCreditScore(user.getCreditScore());
        dto.setAccountStatus(user.getAccountStatus().name());
        dto.setRole(user.getRole().name());
        dto.setCreatedAt(user.getCreatedAt());
        dto.setLastLoginTime(user.getLastLoginTime());
        dto.setGraduationFreezeCount(user.getGraduationFreezeCount());
        dto.setCreditResetUsed(user.getCreditResetUsed());
        return dto;
    }

    private ReviewAuditDTO toReviewAuditDTO(ReviewAudit audit) {
        ReviewAuditDTO dto = new ReviewAuditDTO();
        dto.setId(audit.getId());
        dto.setApplicantId(audit.getApplicantId());
        // Fetch applicant nickname
        userRepository.findById(audit.getApplicantId()).ifPresent(u -> dto.setApplicantNickname(u.getNickname()));
        dto.setAuditType(audit.getAuditType().name());
        dto.setOldValue(audit.getOldValue());
        dto.setNewValue(audit.getNewValue());
        dto.setStatus(audit.getStatus().name());
        dto.setRejectReason(audit.getRejectReason());
        dto.setSubmittedAt(audit.getSubmittedAt());
        dto.setProcessedAt(audit.getProcessedAt());
        return dto;
    }

    private SystemConfigDTO toSystemConfigDTO(SystemConfig config) {
        SystemConfigDTO dto = new SystemConfigDTO();
        dto.setId(config.getId());
        dto.setConfigKey(config.getConfigKey());
        dto.setConfigValue(config.getConfigValue());
        dto.setConfigType(config.getConfigType());
        dto.setDescription(config.getDescription());
        return dto;
    }
}
