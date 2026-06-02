package com.firstteam.taskbountyplatform.report.service;

import com.firstteam.taskbountyplatform.common.enums.AccountRole;
import com.firstteam.taskbountyplatform.common.enums.AuditActionType;
import com.firstteam.taskbountyplatform.audit.service.AuditLogService;
import com.firstteam.taskbountyplatform.auth.security.UserContext;
import com.firstteam.taskbountyplatform.common.enums.CreditChangeReason;
import com.firstteam.taskbountyplatform.common.enums.NotificationType;
import com.firstteam.taskbountyplatform.common.enums.ReportStatus;
import com.firstteam.taskbountyplatform.common.enums.ReportTargetType;
import com.firstteam.taskbountyplatform.common.enums.TaskStatus;
import com.firstteam.taskbountyplatform.common.enums.UserStatus;
import com.firstteam.taskbountyplatform.common.exception.BusinessException;
import com.firstteam.taskbountyplatform.config.PlatformConfig;
import com.firstteam.taskbountyplatform.credit.service.CreditService;
import com.firstteam.taskbountyplatform.notification.service.NotificationService;
import com.firstteam.taskbountyplatform.point.entity.PointAccount;
import com.firstteam.taskbountyplatform.point.entity.PointFlow;
import com.firstteam.taskbountyplatform.common.enums.PointFlowType;
import com.firstteam.taskbountyplatform.point.repository.PointAccountRepository;
import com.firstteam.taskbountyplatform.point.repository.PointFlowRepository;
import com.firstteam.taskbountyplatform.report.dto.ReportProcessRequest;
import com.firstteam.taskbountyplatform.report.dto.ReportSubmitRequest;
import com.firstteam.taskbountyplatform.report.entity.Report;
import com.firstteam.taskbountyplatform.report.repository.ReportRepository;
import com.firstteam.taskbountyplatform.task.entity.Task;
import com.firstteam.taskbountyplatform.task.repository.TaskRepository;
import com.firstteam.taskbountyplatform.user.entity.User;
import com.firstteam.taskbountyplatform.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ReportService {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final PointAccountRepository pointAccountRepository;
    private final PointFlowRepository pointFlowRepository;
    private final CreditService creditService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final UserContext userContext;
    private final PlatformConfig platformConfig;

    public ReportService(ReportRepository reportRepository,
                         UserRepository userRepository,
                         TaskRepository taskRepository,
                         PointAccountRepository pointAccountRepository,
                         PointFlowRepository pointFlowRepository,
                         CreditService creditService,
                         NotificationService notificationService,
                         AuditLogService auditLogService,
                         UserContext userContext,
                         PlatformConfig platformConfig) {
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
        this.taskRepository = taskRepository;
        this.pointAccountRepository = pointAccountRepository;
        this.pointFlowRepository = pointFlowRepository;
        this.creditService = creditService;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
        this.userContext = userContext;
        this.platformConfig = platformConfig;
    }

    @Transactional
    public Report submitReport(ReportSubmitRequest request, Long reporterId) {
        // Validate target exists
        if (request.getTargetType() == ReportTargetType.TASK) {
            taskRepository.findById(request.getTargetId())
                    .orElseThrow(() -> new BusinessException(404, "被举报的任务不存在"));
        } else if (request.getTargetType() == ReportTargetType.USER) {
            userRepository.findById(request.getTargetId())
                    .orElseThrow(() -> new BusinessException(404, "被举报的用户不存在"));
        } else {
            throw new BusinessException(400, "无效的举报目标类型");
        }

        // Cannot report self
        if (request.getTargetType() == ReportTargetType.USER
                && request.getTargetId().equals(reporterId)) {
            throw new BusinessException(400, "不能举报自己");
        }

        Report report = new Report();
        report.setTargetType(request.getTargetType());
        report.setTargetId(request.getTargetId());
        report.setReporterId(reporterId);
        report.setReportType(request.getReportType());
        report.setEvidence(request.getEvidence());
        report.setStatus(ReportStatus.PENDING);
        report.setCreatedAt(LocalDateTime.now());
        report = reportRepository.save(report);

        // Notify all admin users about the new report
        List<User> admins = userRepository.findByRole(AccountRole.ADMIN);
        if (admins != null && !admins.isEmpty()) {
            List<Long> adminIds = admins.stream().map(User::getId).collect(Collectors.toList());
            notificationService.createBatchNotifications(adminIds,
                    NotificationType.REPORT_RESULT,
                    "收到新举报",
                    "用户" + reporterId + "举报了" +
                            request.getTargetType().getDisplayName() + request.getTargetId(),
                    "/admin/reports/" + report.getId());
        }

        auditLogService.log(reporterId, AuditActionType.REPORT_SUBMIT,
                "report", report.getId(),
                "提交举报: targetType=" + request.getTargetType() +
                        ", targetId=" + request.getTargetId(), "127.0.0.1");

        return report;
    }

    public Page<Report> getReports(String status, Pageable pageable, boolean isAdmin) {
        if (isAdmin) {
            if (status != null && !status.isEmpty()) {
                try {
                    ReportStatus reportStatus = ReportStatus.valueOf(status.toUpperCase());
                    return reportRepository.findByStatus(reportStatus, pageable);
                } catch (IllegalArgumentException e) {
                    return reportRepository.findByStatus(ReportStatus.PENDING, pageable);
                }
            }
            return reportRepository.findAll(pageable);
        }
        return reportRepository.findAll(pageable);
    }

    public Report getReportDetail(Long reportId) {
        return reportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(404, "举报不存在"));
    }

    @Transactional
    public void approveReport(Long reportId, Long adminId, ReportProcessRequest request) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(404, "举报不存在"));

        if (report.getStatus() != ReportStatus.PENDING) {
            throw new BusinessException(400, "该举报已被处理");
        }

        report.setStatus(ReportStatus.APPROVED);
        report.setAdminId(adminId);
        report.setAdminNote(request.getAdminNote());
        report.setPenaltyDays(request.getPenaltyDays() != null ? request.getPenaltyDays() : 5);
        report.setCreditPenalty(request.getCreditPenalty() != null ? request.getCreditPenalty() : 5);
        report.setProcessedAt(LocalDateTime.now());
        reportRepository.save(report);

        if (report.getTargetType() == ReportTargetType.TASK) {
            applyTaskReportPenalty(report);
        } else if (report.getTargetType() == ReportTargetType.USER) {
            applyUserReportPenalty(report);
        }

        // Notify reporter
        notificationService.createNotification(report.getReporterId(),
                NotificationType.REPORT_RESULT,
                "举报处理结果",
                "您对" + report.getTargetType().getDisplayName() +
                        report.getTargetId() + "的举报已被核实通过",
                null);

        // Notify reported party if targeting user
        if (report.getTargetType() == ReportTargetType.USER) {
            notificationService.createNotification(report.getTargetId(),
                    NotificationType.REPORT_RESULT,
                    "您被举报了",
                    "您的内容被举报，管理员已核实并处理: " +
                            (request.getAdminNote() != null ? request.getAdminNote() : ""),
                    null);
        }

        auditLogService.log(adminId, AuditActionType.REPORT_SUBMIT,
                "report", reportId,
                "审批通过举报: reportId=" + reportId +
                        ", targetType=" + report.getTargetType() +
                        ", targetId=" + report.getTargetId(), "127.0.0.1");
    }

    @Transactional
    public void rejectReport(Long reportId, Long adminId, String reason) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(404, "举报不存在"));

        if (report.getStatus() != ReportStatus.PENDING) {
            throw new BusinessException(400, "该举报已被处理");
        }

        report.setStatus(ReportStatus.REJECTED);
        report.setAdminId(adminId);
        report.setAdminNote(reason);
        report.setProcessedAt(LocalDateTime.now());
        reportRepository.save(report);

        notificationService.createNotification(report.getReporterId(),
                NotificationType.REPORT_RESULT,
                "举报处理结果",
                "您的举报已被驳回",
                null);

        if (report.getTargetType() == ReportTargetType.USER) {
            notificationService.createNotification(report.getTargetId(),
                    NotificationType.REPORT_RESULT,
                    "举报已驳回",
                    "针对您的举报已被管理员驳回: " + (reason != null ? reason : ""),
                    null);
        }

        auditLogService.log(adminId, AuditActionType.REPORT_SUBMIT,
                "report", reportId,
                "驳回举报: reportId=" + reportId + ", reason=" + reason, "127.0.0.1");
    }

    @Transactional
    public void rejectReportWithPenalty(Long reportId, Long adminId, String reason) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(404, "举报不存在"));

        if (report.getStatus() != ReportStatus.PENDING) {
            throw new BusinessException(400, "该举报已被处理");
        }

        report.setStatus(ReportStatus.REJECTED);
        report.setAdminId(adminId);
        report.setAdminNote(reason);
        report.setProcessedAt(LocalDateTime.now());
        reportRepository.save(report);

        // Deduct reporter 3 credit points
        creditService.addCreditRecord(report.getReporterId(), null, -3,
                CreditChangeReason.FALSE_REPORT,
                "不实举报，扣3分信用分");

        notificationService.createNotification(report.getReporterId(),
                NotificationType.REPORT_RESULT,
                "举报被驳回并扣分",
                "您的举报被认定为不实举报，已扣除3分信用分",
                null);

        if (report.getTargetType() == ReportTargetType.USER) {
            notificationService.createNotification(report.getTargetId(),
                    NotificationType.REPORT_RESULT,
                    "举报已驳回",
                    "针对您的虚假举报已被管理员驳回: " + (reason != null ? reason : ""),
                    null);
        }

        auditLogService.log(adminId, AuditActionType.REPORT_SUBMIT,
                "report", reportId,
                "驳回举报(含扣分处罚): reportId=" + reportId + ", reason=" + reason, "127.0.0.1");
    }

    // ========== Private Helpers ==========

    private void applyTaskReportPenalty(Report report) {
        Task task = taskRepository.findById(report.getTargetId()).orElse(null);
        if (task == null) return;

        Long publisherId = task.getPublisherId();
        int penaltyDays = report.getPenaltyDays() != null ? report.getPenaltyDays() : 5;
        int creditPenalty = report.getCreditPenalty() != null ? report.getCreditPenalty() : 5;

        task.setStatus(TaskStatus.CANCELLED);
        task.setCancelledAt(LocalDateTime.now());
        taskRepository.save(task);

        if (publisherId != null) {
            pointAccountRepository.findByUserIdForUpdate(publisherId).ifPresent(account -> {
                int confiscated = account.getAvailablePoints();
                if (confiscated > 0) {
                    int before = account.getAvailablePoints();
                    account.setAvailablePoints(0);
                    account.setTotalExpense(account.getTotalExpense() + confiscated);
                    pointAccountRepository.save(account);

                    PointFlow flow = new PointFlow();
                    flow.setUserId(publisherId);
                    flow.setTaskId(task.getId());
                    flow.setChangeAmount(-confiscated);
                    flow.setBalanceBefore(before);
                    flow.setBalanceAfter(0);
                    flow.setFlowType(PointFlowType.EXPENSE);
                    flow.setDescription("任务被举报核实，积分全部罚没: taskId=" + task.getId());
                    flow.setCreatedAt(LocalDateTime.now());
                    pointFlowRepository.save(flow);
                }
            });
        }

        User publisher = userRepository.findById(publisherId).orElse(null);
        if (publisher != null) {
            publisher.setAccountStatus(UserStatus.FROZEN);
            publisher.setFrozenUntil(LocalDateTime.now().plusDays(penaltyDays));
            publisher.setFreezeReason("任务被举报核实，冻结" + penaltyDays + "天");
            userRepository.save(publisher);

            notificationService.createNotification(publisherId,
                    NotificationType.FREEZE_NOTICE,
                    "账户被冻结",
                    "您的任务被举报核实，账户已冻结" + penaltyDays + "天",
                    null);
        }

        creditService.addCreditRecord(publisherId, task.getId(), -creditPenalty,
                CreditChangeReason.REPORT_PENALTY,
                "任务被举报处罚，扣" + creditPenalty + "分信用分");
    }

    private void applyUserReportPenalty(Report report) {
        Long reportedUserId = report.getTargetId();
        int penaltyDays = report.getPenaltyDays() != null ? report.getPenaltyDays() : 10;

        User reportedUser = userRepository.findById(reportedUserId).orElse(null);
        if (reportedUser != null) {
            reportedUser.setAnnouncement(null);
            userRepository.save(reportedUser);

            reportedUser.setAccountStatus(UserStatus.FROZEN);
            reportedUser.setFrozenUntil(LocalDateTime.now().plusDays(penaltyDays));
            reportedUser.setFreezeReason("被举报核实，冻结" + penaltyDays + "天");
            userRepository.save(reportedUser);

            notificationService.createNotification(reportedUserId,
                    NotificationType.FREEZE_NOTICE,
                    "账户被冻结",
                    "您被举报核实，账户已冻结" + penaltyDays + "天",
                    null);
        }
    }
}
