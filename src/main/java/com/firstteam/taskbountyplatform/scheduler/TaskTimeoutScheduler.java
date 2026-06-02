package com.firstteam.taskbountyplatform.scheduler;

import com.firstteam.taskbountyplatform.audit.service.AuditLogService;
import com.firstteam.taskbountyplatform.common.enums.AuditActionType;
import com.firstteam.taskbountyplatform.config.PlatformConfig;
import com.firstteam.taskbountyplatform.common.enums.CreditChangeReason;
import com.firstteam.taskbountyplatform.credit.service.CreditService;
import com.firstteam.taskbountyplatform.delivery.entity.Delivery;
import com.firstteam.taskbountyplatform.common.enums.DeliveryStatus;
import com.firstteam.taskbountyplatform.delivery.repository.DeliveryRepository;
import com.firstteam.taskbountyplatform.common.enums.NotificationType;
import com.firstteam.taskbountyplatform.notification.service.NotificationService;
import com.firstteam.taskbountyplatform.point.entity.PointAccount;
import com.firstteam.taskbountyplatform.point.entity.PointFlow;
import com.firstteam.taskbountyplatform.common.enums.PointFlowType;
import com.firstteam.taskbountyplatform.point.repository.PointAccountRepository;
import com.firstteam.taskbountyplatform.point.repository.PointFlowRepository;
import com.firstteam.taskbountyplatform.task.entity.Task;
import com.firstteam.taskbountyplatform.common.enums.TaskStatus;
import com.firstteam.taskbountyplatform.task.repository.TaskApplicationRepository;
import com.firstteam.taskbountyplatform.task.repository.TaskRepository;
import com.firstteam.taskbountyplatform.user.entity.User;
import com.firstteam.taskbountyplatform.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Scheduler that handles all task-related timeout operations:
 * unclaimed task auto-cancel, delivery timeout warnings,
 * pending confirmation auto-complete, and deadline reminders.
 */
@Component
public class TaskTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(TaskTimeoutScheduler.class);

    private final TaskRepository taskRepository;
    private final TaskApplicationRepository taskApplicationRepository;
    private final DeliveryRepository deliveryRepository;
    private final PointAccountRepository pointAccountRepository;
    private final PointFlowRepository pointFlowRepository;
    private final CreditService creditService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final PlatformConfig platformConfig;
    private final UserRepository userRepository;

    public TaskTimeoutScheduler(TaskRepository taskRepository,
                                TaskApplicationRepository taskApplicationRepository,
                                DeliveryRepository deliveryRepository,
                                PointAccountRepository pointAccountRepository,
                                PointFlowRepository pointFlowRepository,
                                CreditService creditService,
                                NotificationService notificationService,
                                AuditLogService auditLogService,
                                PlatformConfig platformConfig,
                                UserRepository userRepository) {
        this.taskRepository = taskRepository;
        this.taskApplicationRepository = taskApplicationRepository;
        this.deliveryRepository = deliveryRepository;
        this.pointAccountRepository = pointAccountRepository;
        this.pointFlowRepository = pointFlowRepository;
        this.creditService = creditService;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
        this.platformConfig = platformConfig;
        this.userRepository = userRepository;
    }

    /**
     * Every 5 minutes: find published tasks that have exceeded their auto-cancel
     * deadline and cancel them, returning frozen points to the publisher.
     */
    @Scheduled(cron = "0 */5 * * * ?")
    public void checkUnclaimedTaskTimeout() {
        log.info("Starting checkUnclaimedTaskTimeout...");
        int autoCancelDays = platformConfig.getTask().getAutoCancelDays();
        LocalDateTime cutoff = LocalDateTime.now().minusDays(autoCancelDays);

        List<Task> expiredTasks = taskRepository.findExpiredPublishedTasks(cutoff);
        log.info("Found {} published tasks past auto-cancel deadline", expiredTasks.size());

        for (Task task : expiredTasks) {
            try {
                cancelUnclaimedTask(task);
            } catch (Exception e) {
                log.error("Failed to auto-cancel unclaimed task id={}: {}", task.getId(), e.getMessage(), e);
            }
        }
        log.info("checkUnclaimedTaskTimeout completed. Processed {} tasks", expiredTasks.size());
    }

    @Transactional
    protected void cancelUnclaimedTask(Task task) {
        LocalDateTime now = LocalDateTime.now();

        // Update task status to CANCELLED
        task.setStatus(TaskStatus.CANCELLED);
        task.setCancelledAt(now);
        taskRepository.save(task);

        // Unfreeze points: move frozen_points back to available_points for publisher
        Optional<PointAccount> accountOpt = pointAccountRepository.findByUserIdForUpdate(task.getPublisherId());
        if (accountOpt.isPresent()) {
            PointAccount account = accountOpt.get();
            int frozen = account.getFrozenPoints();
            int reward = task.getRewardPoints();
            int unfreezeAmount = Math.min(frozen, reward);

            if (unfreezeAmount > 0) {
                int balanceBefore = account.getAvailablePoints();
                account.setFrozenPoints(account.getFrozenPoints() - unfreezeAmount);
                account.setAvailablePoints(account.getAvailablePoints() + unfreezeAmount);
                pointAccountRepository.save(account);

                // Record point flow for unfreeze
                PointFlow flow = new PointFlow();
                flow.setUserId(task.getPublisherId());
                flow.setTaskId(task.getId());
                flow.setChangeAmount(unfreezeAmount);
                flow.setBalanceBefore(balanceBefore);
                flow.setBalanceAfter(account.getAvailablePoints());
                flow.setFlowType(PointFlowType.UNFREEZE);
                flow.setDescription("Task #" + task.getId() + " auto-cancelled, frozen points returned");
                pointFlowRepository.save(flow);
            }
        }

        // Notify publisher
        try {
            notificationService.createNotification(
                    task.getPublisherId(),
                    NotificationType.SYSTEM_NOTICE,
                    "任务已自动取消",
                    "您的任务「" + task.getTitle() + "」超过" + task.getAutoCancelDays()
                            + "天无人接单，已被系统自动取消，冻结积分已退回。",
                    "/tasks/" + task.getId()
            );
        } catch (Exception e) {
            log.warn("Failed to send cancellation notification for task id={}: {}", task.getId(), e.getMessage());
        }

        // Record audit log
        try {
            auditLogService.log(
                    AuditActionType.SYSTEM_AUTO_CANCEL,
                    "TASK",
                    task.getId(),
                    "Task #" + task.getId() + " auto-cancelled after " + task.getAutoCancelDays()
                            + " days unclaimed. Reward: " + task.getRewardPoints() + " points returned."
            );
        } catch (Exception e) {
            log.warn("Failed to record audit log for task id={}: {}", task.getId(), e.getMessage());
        }
    }

    /**
     * Every 5 minutes: check IN_PROGRESS tasks whose deadline has passed
     * and notify both publisher and worker that the task is overdue.
     */
    @Scheduled(cron = "0 */5 * * * ?")
    public void checkDeliveryTimeout() {
        log.info("Starting checkDeliveryTimeout...");
        LocalDateTime now = LocalDateTime.now();

        List<Task> overdueTasks = taskRepository.findOverdueTasks(now);
        log.info("Found {} overdue IN_PROGRESS tasks", overdueTasks.size());

        for (Task task : overdueTasks) {
            try {
                handleOverdueTask(task);
            } catch (Exception e) {
                log.error("Failed to handle overdue task id={}: {}", task.getId(), e.getMessage(), e);
            }
        }
        log.info("checkDeliveryTimeout completed. Processed {} tasks", overdueTasks.size());
    }

    @Transactional
    protected void handleOverdueTask(Task task) {
        if (task.getWinnerId() == null) {
            log.warn("Overdue task id={} has no winner, skipping", task.getId());
            return;
        }

        // Notify publisher that they can force-cancel
        try {
            notificationService.createNotification(
                    task.getPublisherId(),
                    NotificationType.TASK_UPDATE,
                    "任务已超时，您可以强制取消",
                    "您的任务「" + task.getTitle() + "」已超过截止时间，接单者尚未完成交付。"
                            + "您可以选择强制取消此任务并收回冻结积分。",
                    "/tasks/" + task.getId()
            );
        } catch (Exception e) {
            log.warn("Failed to send overdue notification to publisher for task id={}: {}",
                    task.getId(), e.getMessage());
        }

        // Notify worker that task is overdue
        try {
            long overdueMinutes = ChronoUnit.MINUTES.between(task.getDeadlineAt(), LocalDateTime.now());
            notificationService.createNotification(
                    task.getWinnerId(),
                    NotificationType.TASK_UPDATE,
                    "任务已超时，请尽快完成交付",
                    "您接取的任务「" + task.getTitle() + "」已超时" + overdueMinutes
                            + "分钟，请尽快提交交付，否则发布者可能会强制取消任务。",
                    "/tasks/" + task.getId()
            );
        } catch (Exception e) {
            log.warn("Failed to send overdue notification to worker for task id={}: {}",
                    task.getId(), e.getMessage());
        }
    }

    /**
     * Every 5 minutes: check PENDING_CONFIRMATION tasks whose latest delivery
     * has been waiting for confirmation longer than the auto-confirm window.
     */
    @Scheduled(cron = "0 */5 * * * ?")
    public void checkPendingConfirmationTimeout() {
        log.info("Starting checkPendingConfirmationTimeout...");
        int autoConfirmHours = platformConfig.getTask().getAutoConfirmHours();
        LocalDateTime now = LocalDateTime.now();

        List<Task> pendingTasks = taskRepository.findTasksPendingConfirmation();
        log.info("Found {} PENDING_CONFIRMATION tasks", pendingTasks.size());

        for (Task task : pendingTasks) {
            try {
                // Get the latest delivery for this task
                Optional<Delivery> latestDeliveryOpt =
                        deliveryRepository.findFirstByTaskIdOrderBySubmitTimeDesc(task.getId());
                if (latestDeliveryOpt.isEmpty()) {
                    log.warn("Task id={} is PENDING_CONFIRMATION but has no deliveries, skipping", task.getId());
                    continue;
                }
                Delivery latestDelivery = latestDeliveryOpt.get();
                LocalDateTime timeoutAt = latestDelivery.getSubmitTime().plusHours(autoConfirmHours);

                if (timeoutAt.isBefore(now)) {
                    autoConfirmTask(task, latestDelivery);
                }
            } catch (Exception e) {
                log.error("Failed to auto-confirm task id={}: {}", task.getId(), e.getMessage(), e);
            }
        }
        log.info("checkPendingConfirmationTimeout completed. Processed {} tasks", pendingTasks.size());
    }

    @Transactional
    protected void autoConfirmTask(Task task, Delivery delivery) {
        if (task.getWinnerId() == null) {
            log.warn("Task id={} has no winner, cannot auto-confirm", task.getId());
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        // Transfer points from publisher's frozen balance to worker
        Optional<PointAccount> publisherAccountOpt =
                pointAccountRepository.findByUserIdForUpdate(task.getPublisherId());
        if (publisherAccountOpt.isEmpty()) {
            log.error("Publisher account not found for task id={}, publisherId={}",
                    task.getId(), task.getPublisherId());
            return;
        }
        PointAccount publisherAccount = publisherAccountOpt.get();

        int reward = task.getRewardPoints();
        int frozen = publisherAccount.getFrozenPoints();
        int deductAmount = Math.min(frozen, reward);

        // Deduct from publisher's frozen points
        if (deductAmount > 0) {
            publisherAccount.setFrozenPoints(publisherAccount.getFrozenPoints() - deductAmount);
            publisherAccount.setTotalExpense(publisherAccount.getTotalExpense() + deductAmount);
            pointAccountRepository.save(publisherAccount);

            // Record point flow for publisher expense
            PointFlow publisherFlow = new PointFlow();
            publisherFlow.setUserId(task.getPublisherId());
            publisherFlow.setTaskId(task.getId());
            publisherFlow.setChangeAmount(-deductAmount);
            publisherFlow.setBalanceBefore(publisherAccount.getAvailablePoints());
            publisherFlow.setBalanceAfter(publisherAccount.getAvailablePoints());
            publisherFlow.setFlowType(PointFlowType.EXPENSE);
            publisherFlow.setDescription("Task #" + task.getId() + " auto-completed, points transferred to worker");
            pointFlowRepository.save(publisherFlow);
        }

        // Add points to worker's available balance
        Optional<PointAccount> workerAccountOpt =
                pointAccountRepository.findByUserIdForUpdate(task.getWinnerId());
        if (workerAccountOpt.isPresent()) {
            PointAccount workerAccount = workerAccountOpt.get();
            int balanceBefore = workerAccount.getAvailablePoints();
            workerAccount.setAvailablePoints(workerAccount.getAvailablePoints() + reward);
            workerAccount.setTotalIncome(workerAccount.getTotalIncome() + reward);
            pointAccountRepository.save(workerAccount);

            // Record point flow for worker income
            PointFlow workerFlow = new PointFlow();
            workerFlow.setUserId(task.getWinnerId());
            workerFlow.setTaskId(task.getId());
            workerFlow.setChangeAmount(reward);
            workerFlow.setBalanceBefore(balanceBefore);
            workerFlow.setBalanceAfter(workerAccount.getAvailablePoints());
            workerFlow.setFlowType(PointFlowType.INCOME);
            workerFlow.setDescription("Task #" + task.getId() + " auto-completed, reward received");
            pointFlowRepository.save(workerFlow);
        } else {
            log.error("Worker account not found for task id={}, winnerId={}",
                    task.getId(), task.getWinnerId());
        }

        // Update delivery status to APPROVED
        delivery.setStatus(DeliveryStatus.ACCEPTED);
        deliveryRepository.save(delivery);

        // Update task status to COMPLETED
        task.setStatus(TaskStatus.COMPLETED);
        task.setCompletedAt(now);
        taskRepository.save(task);

        // Notify publisher
        try {
            notificationService.createNotification(
                    task.getPublisherId(),
                    NotificationType.TASK_UPDATE,
                    "任务已自动确认完成",
                    "您的任务「" + task.getTitle() + "」提交的交付物超过" +
                            platformConfig.getTask().getAutoConfirmHours() +
                            "小时未确认，已由系统自动确认完成。请为接单者留下评价。",
                    "/tasks/" + task.getId() + "/review"
            );
        } catch (Exception e) {
            log.warn("Failed to send auto-complete notification to publisher for task id={}: {}",
                    task.getId(), e.getMessage());
        }

        // Notify worker
        try {
            notificationService.createNotification(
                    task.getWinnerId(),
                    NotificationType.TASK_UPDATE,
                    "任务已自动确认完成",
                    "您接取的任务「" + task.getTitle() + "」已由系统自动确认完成，"
                            + "奖励积分已到账。请为发布者留下评价。",
                    "/tasks/" + task.getId() + "/review"
            );
        } catch (Exception e) {
            log.warn("Failed to send auto-complete notification to worker for task id={}: {}",
                    task.getId(), e.getMessage());
        }

        // Credit score adjustments
        try {
            creditService.addCreditRecord(task.getPublisherId(), task.getId(), 0,
                    CreditChangeReason.SYSTEM_ADJUST,
                    "Task #" + task.getId() + " system auto-completed (publisher)");
            creditService.addCreditRecord(task.getWinnerId(), task.getId(), 0,
                    CreditChangeReason.SYSTEM_ADJUST,
                    "Task #" + task.getId() + " system auto-completed (worker)");
        } catch (Exception e) {
            log.warn("Failed to adjust credit scores for task id={}: {}", task.getId(), e.getMessage());
        }

        // Record audit log
        try {
            auditLogService.log(
                    AuditActionType.SYSTEM_AUTO_COMPLETE,
                    "TASK",
                    task.getId(),
                    "Task #" + task.getId() + " auto-completed after " +
                            platformConfig.getTask().getAutoConfirmHours() +
                            " hours pending confirmation. Delivery #" + delivery.getId()
            );
        } catch (Exception e) {
            log.warn("Failed to record audit log for auto-complete task id={}: {}", task.getId(), e.getMessage());
        }
    }

    /**
     * Daily at 1:00 AM: send deadline reminders for tasks whose deadline
     * is within the next 24 hours.
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void sendDeadlineReminders() {
        log.info("Starting sendDeadlineReminders...");
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime warningTime = now.plusHours(24);

        List<Task> nearingTasks = taskRepository.findTasksNearingDeadline(now, warningTime);
        log.info("Found {} tasks with deadline within next 24 hours", nearingTasks.size());

        for (Task task : nearingTasks) {
            try {
                sendDeadlineReminder(task);
            } catch (Exception e) {
                log.error("Failed to send deadline reminder for task id={}: {}", task.getId(), e.getMessage(), e);
            }
        }
        log.info("sendDeadlineReminders completed. Processed {} tasks", nearingTasks.size());
    }

    protected void sendDeadlineReminder(Task task) {
        if (task.getDeadlineAt() == null) {
            return;
        }

        long hoursRemaining = ChronoUnit.HOURS.between(LocalDateTime.now(), task.getDeadlineAt());
        if (hoursRemaining < 0) {
            hoursRemaining = 0;
        }

        String reminderContent = "您的任务「" + task.getTitle() + "」截止时间临近，"
                + "还剩约 " + hoursRemaining + " 小时。";

        // Notify publisher
        if (task.getPublisherId() != null) {
            try {
                notificationService.createNotification(
                        task.getPublisherId(),
                        NotificationType.TASK_UPDATE,
                        "任务截止提醒",
                        reminderContent + "请关注任务进展。",
                        "/tasks/" + task.getId()
                );
            } catch (Exception e) {
                log.warn("Failed to send deadline reminder to publisher for task id={}: {}",
                        task.getId(), e.getMessage());
            }
        }

        // Notify worker
        if (task.getWinnerId() != null) {
            try {
                notificationService.createNotification(
                        task.getWinnerId(),
                        NotificationType.TASK_UPDATE,
                        "任务截止提醒",
                        reminderContent + "请尽快完成交付。",
                        "/tasks/" + task.getId()
                );
            } catch (Exception e) {
                log.warn("Failed to send deadline reminder to worker for task id={}: {}",
                        task.getId(), e.getMessage());
            }
        }
    }

    /**
     * Every hour: send pre-deadline urgent reminders for tasks approaching deadline.
     * Reminder time = min(24h, deadlineMinutes * 50%) and at least 5 minutes before deadline.
     */
    @Scheduled(cron = "0 0 */1 * * ?")
    public void sendPreDeadlineReminders() {
        log.info("Starting sendPreDeadlineReminders...");
        LocalDateTime now = LocalDateTime.now();

        // Find all IN_PROGRESS tasks with a deadline in the future
        List<Task> inProgressTasks = taskRepository.findTasksNearingDeadline(now, now.plusDays(30));
        int remindedCount = 0;

        for (Task task : inProgressTasks) {
            try {
                if (shouldSendPreDeadlineReminder(task, now)) {
                    sendPreDeadlineReminder(task);
                    remindedCount++;
                }
            } catch (Exception e) {
                log.error("Failed to send pre-deadline reminder for task id={}: {}",
                        task.getId(), e.getMessage(), e);
            }
        }
        log.info("sendPreDeadlineReminders completed. Sent {} urgent reminders", remindedCount);
    }

    /**
     * Determine if an urgent pre-deadline reminder should be sent.
     * Uses: reminder time = min(24h, deadlineMinutes * 50%) and at least 5 min before deadline.
     */
    protected boolean shouldSendPreDeadlineReminder(Task task, LocalDateTime now) {
        if (task.getDeadlineAt() == null || task.getWinnerId() == null) {
            return false;
        }

        // Calculate the pre-deadline warning threshold
        long deadlineMinutesTotal = task.getDeadlineMinutes() + task.getExtendTotalMinutes();
        long fiftyPercentMinutes = deadlineMinutesTotal * 50 / 100;
        long reminderHours = Math.min(24 * 60, fiftyPercentMinutes);
        // Ensure at least 5 minutes
        reminderHours = Math.max(5, reminderHours);

        LocalDateTime reminderTime = task.getDeadlineAt().minusMinutes(reminderHours);

        // Only remind if we are within a reasonable window around the reminder time
        // (within the last 60 minutes since this job runs hourly)
        if (now.isBefore(reminderTime)) {
            return false;
        }
        if (now.isAfter(reminderTime.plusMinutes(60))) {
            // Reminder time passed more than 1 hour ago, already handled in a previous run
            return false;
        }

        // Don't remind if deadline has already passed
        if (now.isAfter(task.getDeadlineAt())) {
            return false;
        }

        return true;
    }

    protected void sendPreDeadlineReminder(Task task) {
        if (task.getWinnerId() == null) {
            return;
        }

        long minutesRemaining = ChronoUnit.MINUTES.between(LocalDateTime.now(), task.getDeadlineAt());
        if (minutesRemaining < 0) {
            minutesRemaining = 0;
        }

        String urgencyNote = minutesRemaining < 60
                ? "任务即将截止，请立即完成交付！"
                : "请合理安排时间尽快完成。";

        try {
            notificationService.createNotification(
                    task.getWinnerId(),
                    NotificationType.TASK_UPDATE,
                    "紧急任务截止提醒",
                    "您的任务「" + task.getTitle() + "」仅剩约 " + minutesRemaining
                            + " 分钟截止。" + urgencyNote,
                    "/tasks/" + task.getId()
            );
        } catch (Exception e) {
            log.warn("Failed to send pre-deadline urgent reminder for task id={}: {}",
                    task.getId(), e.getMessage());
        }
    }
}
