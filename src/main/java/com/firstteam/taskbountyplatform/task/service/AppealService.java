package com.firstteam.taskbountyplatform.task.service;

import com.firstteam.taskbountyplatform.common.enums.AuditActionType;
import com.firstteam.taskbountyplatform.audit.service.AuditLogService;
import com.firstteam.taskbountyplatform.auth.security.UserContext;
import com.firstteam.taskbountyplatform.common.enums.AccountRole;
import com.firstteam.taskbountyplatform.common.enums.CreditChangeReason;
import com.firstteam.taskbountyplatform.common.enums.NotificationType;
import com.firstteam.taskbountyplatform.common.enums.TaskStatus;
import com.firstteam.taskbountyplatform.common.exception.BusinessException;
import com.firstteam.taskbountyplatform.credit.service.CreditService;
import com.firstteam.taskbountyplatform.notification.service.NotificationService;
import com.firstteam.taskbountyplatform.point.entity.PointAccount;
import com.firstteam.taskbountyplatform.point.entity.PointFlow;
import com.firstteam.taskbountyplatform.common.enums.PointFlowType;
import com.firstteam.taskbountyplatform.point.repository.PointAccountRepository;
import com.firstteam.taskbountyplatform.point.repository.PointFlowRepository;
import com.firstteam.taskbountyplatform.task.dto.AppealProcessRequest;
import com.firstteam.taskbountyplatform.task.entity.Task;
import com.firstteam.taskbountyplatform.task.entity.TaskAppeal;
import com.firstteam.taskbountyplatform.task.repository.TaskAppealRepository;
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
public class AppealService {

    private final TaskAppealRepository taskAppealRepository;
    private final TaskRepository taskRepository;
    private final PointAccountRepository pointAccountRepository;
    private final PointFlowRepository pointFlowRepository;
    private final CreditService creditService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final UserContext userContext;
    private final UserRepository userRepository;

    public AppealService(TaskAppealRepository taskAppealRepository,
                         TaskRepository taskRepository,
                         PointAccountRepository pointAccountRepository,
                         PointFlowRepository pointFlowRepository,
                         CreditService creditService,
                         NotificationService notificationService,
                         AuditLogService auditLogService,
                         UserContext userContext,
                         UserRepository userRepository) {
        this.taskAppealRepository = taskAppealRepository;
        this.taskRepository = taskRepository;
        this.pointAccountRepository = pointAccountRepository;
        this.pointFlowRepository = pointFlowRepository;
        this.creditService = creditService;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
        this.userContext = userContext;
        this.userRepository = userRepository;
    }

    /**
     * Get pending appeals with pagination (admin only).
     */
    public Page<TaskAppeal> getPendingAppeals(Pageable pageable) {
        return taskAppealRepository.findByStatus("PENDING", pageable);
    }

    /**
     * Get full appeal detail with associated task info.
     */
    public TaskAppeal getAppealDetail(Long appealId) {
        return taskAppealRepository.findById(appealId)
                .orElseThrow(() -> new BusinessException(404, "申诉不存在"));
    }

    /**
     * Get all appeals for a specific task.
     */
    public List<TaskAppeal> getAppealsForTask(Long taskId) {
        return taskAppealRepository.findByTaskId(taskId);
    }

    /**
     * Process an appeal (admin only).
     * Decision can be: COMPLETED (transfer points), CANCELLED (return points), or IN_PROGRESS (return to work).
     */
    @Transactional
    public void processAppeal(Long appealId, Long adminId, AppealProcessRequest request) {
        TaskAppeal appeal = taskAppealRepository.findById(appealId)
                .orElseThrow(() -> new BusinessException(404, "申诉不存在"));

        if (!"PENDING".equals(appeal.getStatus())) {
            throw new BusinessException(400, "该申诉已被处理");
        }

        Task task = taskRepository.findById(appeal.getTaskId())
                .orElseThrow(() -> new BusinessException(404, "关联任务不存在"));

        String decision = request.getDecision().toUpperCase();

        switch (decision) {
            case "COMPLETED":
                processAppealCompleted(appeal, task, adminId, request.getAdminNote());
                break;
            case "CANCELLED":
                processAppealCancelled(appeal, task, adminId, request.getAdminNote());
                break;
            case "IN_PROGRESS":
                processAppealReturnToProgress(appeal, task, adminId, request.getAdminNote());
                break;
            default:
                throw new BusinessException(400, "无效的裁定决定: " + decision +
                        " (允许: COMPLETED, CANCELLED, IN_PROGRESS)");
        }

        // Update appeal record
        appeal.setStatus("RESOLVED");
        appeal.setAdminId(adminId);
        appeal.setAdminNote(request.getAdminNote());
        appeal.setResolvedAt(LocalDateTime.now());
        taskAppealRepository.save(appeal);

        // Notify both parties
        User appealer = userRepository.findById(appeal.getAppealerId()).orElse(null);
        String appealerName = appealer != null ? appealer.getNickname() : "用户";

        notificationService.createNotification(appeal.getAppealerId(),
                NotificationType.APPEAL_RESULT,
                "申诉结果",
                "您对任务" + task.getTitle() + "的申诉已被处理: " + decision,
                "/tasks/" + task.getId());

        // Notify the other party
        Long otherPartyId = appeal.getAppealerId().equals(task.getPublisherId())
                ? task.getWinnerId() : task.getPublisherId();
        if (otherPartyId != null) {
            notificationService.createNotification(otherPartyId,
                    NotificationType.APPEAL_RESULT,
                    "申诉结果",
                    "任务" + task.getTitle() + "的申诉结果: " + decision,
                    "/tasks/" + task.getId());
        }

        // Record audit log
        auditLogService.log(adminId, AuditActionType.APPEAL_SUBMIT,
                "appeal", appealId,
                "处理申诉: decision=" + decision +
                        ", taskId=" + task.getId() +
                        ", appealerId=" + appeal.getAppealerId(), "127.0.0.1");
    }

    // ========== Private Helper Methods ==========

    /**
     * Process appeal with COMPLETED decision.
     * Transfer points from publisher to worker, mark task COMPLETED.
     */
    private void processAppealCompleted(TaskAppeal appeal, Task task, Long adminId, String adminNote) {
        Long publisherId = task.getPublisherId();
        Long winnerId = task.getWinnerId();
        Integer rewardPoints = task.getRewardPoints();

        if (winnerId == null) {
            throw new BusinessException(400, "任务无中标者，无法转移积分");
        }

        // Transfer points from publisher to worker
        // Deduct from publisher
        pointAccountRepository.findByUserIdForUpdate(publisherId).ifPresent(pubAccount -> {
            int availableBefore = pubAccount.getAvailablePoints();
            if (availableBefore >= rewardPoints) {
                pubAccount.setAvailablePoints(availableBefore - rewardPoints);
                pubAccount.setTotalExpense(pubAccount.getTotalExpense() + rewardPoints);
                pointAccountRepository.save(pubAccount);

                PointFlow flow = new PointFlow();
                flow.setUserId(publisherId);
                flow.setTaskId(task.getId());
                flow.setChangeAmount(-rewardPoints);
                flow.setBalanceBefore(availableBefore);
                flow.setBalanceAfter(availableBefore - rewardPoints);
                flow.setFlowType(PointFlowType.EXPENSE);
                flow.setDescription("申诉裁定完成，积分转给中标者: taskId=" + task.getId());
                flow.setCreatedAt(LocalDateTime.now());
                pointFlowRepository.save(flow);
            }
        });

        // Credit to winner
        pointAccountRepository.findByUserIdForUpdate(winnerId).ifPresent(winAccount -> {
            int availableBefore = winAccount.getAvailablePoints();
            winAccount.setAvailablePoints(availableBefore + rewardPoints);
            winAccount.setTotalIncome(winAccount.getTotalIncome() + rewardPoints);
            pointAccountRepository.save(winAccount);

            PointFlow flow = new PointFlow();
            flow.setUserId(winnerId);
            flow.setTaskId(task.getId());
            flow.setChangeAmount(rewardPoints);
            flow.setBalanceBefore(availableBefore);
            flow.setBalanceAfter(availableBefore + rewardPoints);
            flow.setFlowType(PointFlowType.INCOME);
            flow.setDescription("申诉裁定完成，获得发布者积分: taskId=" + task.getId());
            flow.setCreatedAt(LocalDateTime.now());
            pointFlowRepository.save(flow);
        });

        // Mark task COMPLETED
        task.setStatus(TaskStatus.COMPLETED);
        task.setCompletedAt(LocalDateTime.now());
        // Freeze time: when transitioning from APPEALING to COMPLETED,
        // freeze time is returned (no time deducted during appeal)
        if (task.getAppealAt() != null && task.getDeadlineAt() != null) {
            long frozenMinutes = java.time.Duration.between(task.getAppealAt(), LocalDateTime.now()).toMinutes();
            task.setDeadlineAt(task.getDeadlineAt().plusMinutes(frozenMinutes));
        }
        task.setAppealAt(null);
        taskRepository.save(task);

        // Notify winner the task is completed
        notificationService.createNotification(winnerId,
                NotificationType.TASK_COMPLETED,
                "任务已完成",
                "任务" + task.getTitle() + "经申诉裁定已完成，积分已到账",
                "/tasks/" + task.getId());
    }

    /**
     * Process appeal with CANCELLED decision.
     * Return points to publisher, mark task CANCELLED.
     */
    private void processAppealCancelled(TaskAppeal appeal, Task task, Long adminId, String adminNote) {
        Long publisherId = task.getPublisherId();
        Long winnerId = task.getWinnerId();
        Integer rewardPoints = task.getRewardPoints();

        // Return frozen points to publisher
        if (publisherId != null) {
            pointAccountRepository.findByUserIdForUpdate(publisherId).ifPresent(pubAccount -> {
                int availableBefore = pubAccount.getAvailablePoints();
                // Unfreeze: move frozen to available
                int frozenPoints = pubAccount.getFrozenPoints();
                pubAccount.setAvailablePoints(availableBefore + rewardPoints);
                pubAccount.setFrozenPoints(0);
                pubAccount.setTotalExpense(pubAccount.getTotalExpense() - rewardPoints);
                pointAccountRepository.save(pubAccount);

                PointFlow flow = new PointFlow();
                flow.setUserId(publisherId);
                flow.setTaskId(task.getId());
                flow.setChangeAmount(rewardPoints);
                flow.setBalanceBefore(availableBefore);
                flow.setBalanceAfter(availableBefore + rewardPoints);
                flow.setFlowType(PointFlowType.UNFREEZE);
                flow.setDescription("申诉裁定取消，积分退还: taskId=" + task.getId());
                flow.setCreatedAt(LocalDateTime.now());
                pointFlowRepository.save(flow);
            });
        }

        // Mark task CANCELLED
        task.setStatus(TaskStatus.CANCELLED);
        task.setCancelledAt(LocalDateTime.now());
        if (task.getAppealAt() != null && task.getDeadlineAt() != null) {
            long frozenMinutes = java.time.Duration.between(task.getAppealAt(), LocalDateTime.now()).toMinutes();
            task.setDeadlineAt(task.getDeadlineAt().plusMinutes(frozenMinutes));
        }
        task.setAppealAt(null);
        taskRepository.save(task);

        // Apply penalty to the losing party (the one who filed the appeal)
        creditService.addCreditRecord(appeal.getAppealerId(), task.getId(), -5,
                CreditChangeReason.APPEAL_PENALTY,
                "申诉被驳回，任务取消，扣5分信用分");

        if (winnerId != null) {
            notificationService.createNotification(winnerId,
                    NotificationType.TASK_CANCELLED,
                    "任务已取消",
                    "任务" + task.getTitle() + "经申诉裁定已取消",
                    "/tasks/" + task.getId());
        }
    }

    /**
     * Process appeal with IN_PROGRESS decision.
     * Return task to IN_PROGRESS state. Appeal time counts as task time.
     */
    private void processAppealReturnToProgress(TaskAppeal appeal, Task task, Long adminId, String adminNote) {
        // Return task to IN_PROGRESS state
        task.setStatus(TaskStatus.IN_PROGRESS);
        // Appeal time counts as task time - no time adjustment needed
        task.setAppealAt(null);
        taskRepository.save(task);

        // Notify winner to continue working
        if (task.getWinnerId() != null) {
            notificationService.createNotification(task.getWinnerId(),
                    NotificationType.APPEAL_RESULT,
                    "任务恢复进行中",
                    "任务" + task.getTitle() + "经申诉裁定恢复进行中状态，请继续完成",
                    "/tasks/" + task.getId());
        }
    }
}
