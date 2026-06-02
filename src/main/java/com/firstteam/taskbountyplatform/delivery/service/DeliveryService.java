package com.firstteam.taskbountyplatform.delivery.service;

import com.firstteam.taskbountyplatform.common.enums.AuditActionType;
import com.firstteam.taskbountyplatform.audit.entity.AuditLog;
import com.firstteam.taskbountyplatform.audit.repository.AuditLogRepository;
import com.firstteam.taskbountyplatform.auth.security.UserContext;
import com.firstteam.taskbountyplatform.common.enums.TaskStatus;
import com.firstteam.taskbountyplatform.config.PlatformConfig;
import com.firstteam.taskbountyplatform.delivery.entity.Delivery;
import com.firstteam.taskbountyplatform.common.enums.DeliveryStatus;
import com.firstteam.taskbountyplatform.delivery.repository.DeliveryRepository;
import com.firstteam.taskbountyplatform.file.entity.FileObject;
import com.firstteam.taskbountyplatform.file.repository.FileObjectRepository;
import com.firstteam.taskbountyplatform.notification.entity.Notification;
import com.firstteam.taskbountyplatform.common.enums.NotificationType;
import com.firstteam.taskbountyplatform.notification.repository.NotificationRepository;
import com.firstteam.taskbountyplatform.point.entity.PointAccount;
import com.firstteam.taskbountyplatform.point.entity.PointFlow;
import com.firstteam.taskbountyplatform.common.enums.PointFlowType;
import com.firstteam.taskbountyplatform.point.repository.PointAccountRepository;
import com.firstteam.taskbountyplatform.point.repository.PointFlowRepository;
import com.firstteam.taskbountyplatform.task.entity.Task;
import com.firstteam.taskbountyplatform.task.repository.TaskApplicationRepository;
import com.firstteam.taskbountyplatform.task.repository.TaskRepository;
import com.firstteam.taskbountyplatform.user.entity.User;
import com.firstteam.taskbountyplatform.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class DeliveryService {

    private final TaskRepository taskRepository;
    private final DeliveryRepository deliveryRepository;
    private final FileObjectRepository fileObjectRepository;
    private final PointAccountRepository pointAccountRepository;
    private final PointFlowRepository pointFlowRepository;
    private final TaskApplicationRepository taskApplicationRepository;
    private final NotificationRepository notificationRepository;
    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final UserContext userContext;
    private final PlatformConfig platformConfig;

    private static final String UPLOAD_DIR = "./uploads/deliveries";

    public DeliveryService(TaskRepository taskRepository,
                           DeliveryRepository deliveryRepository,
                           FileObjectRepository fileObjectRepository,
                           PointAccountRepository pointAccountRepository,
                           PointFlowRepository pointFlowRepository,
                           TaskApplicationRepository taskApplicationRepository,
                           NotificationRepository notificationRepository,
                           AuditLogRepository auditLogRepository,
                           UserRepository userRepository,
                           UserContext userContext,
                           PlatformConfig platformConfig) {
        this.taskRepository = taskRepository;
        this.deliveryRepository = deliveryRepository;
        this.fileObjectRepository = fileObjectRepository;
        this.pointAccountRepository = pointAccountRepository;
        this.pointFlowRepository = pointFlowRepository;
        this.taskApplicationRepository = taskApplicationRepository;
        this.notificationRepository = notificationRepository;
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.userContext = userContext;
        this.platformConfig = platformConfig;
    }

    /**
     * Worker submits a delivery for a task.
     */
    @Transactional
    public Delivery submitDelivery(Long taskId, Long workerId, String description, List<MultipartFile> files) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("任务不存在"));

        // Validate task status
        if (task.getStatus() != TaskStatus.IN_PROGRESS) {
            throw new RuntimeException("任务当前状态不允许提交交付物，当前状态：" + task.getStatus().getDisplayName());
        }

        // Validate worker is the winner
        if (task.getWinnerId() == null || !task.getWinnerId().equals(workerId)) {
            throw new RuntimeException("只有中标者才能提交交付物");
        }

        // Count previous rejections for this task
        List<Delivery> previousDeliveries = deliveryRepository.findByTaskIdOrderBySubmitTimeDesc(taskId);
        long rejectionCount = previousDeliveries.stream()
                .filter(d -> d.getStatus() == DeliveryStatus.REJECTED)
                .count();

        // Save uploaded files
        List<FileObject> savedFiles = new ArrayList<>();
        if (files != null && !files.isEmpty()) {
            for (MultipartFile file : files) {
                if (file.isEmpty()) {
                    continue;
                }
                try {
                    FileObject fileObject = saveFile(file, taskId);
                    savedFiles.add(fileObject);
                } catch (IOException e) {
                    throw new RuntimeException("文件上传失败：" + file.getOriginalFilename(), e);
                }
            }
        }

        // Create Delivery entity
        LocalDateTime now = LocalDateTime.now();
        Delivery delivery = new Delivery();
        delivery.setTaskId(taskId);
        delivery.setWorkerId(workerId);
        delivery.setDescription(description);
        delivery.setStatus(DeliveryStatus.SUBMITTED);
        delivery.setSubmitTime(now);
        delivery.setRejectCount(0);
        deliveryRepository.save(delivery);

        // Link files to delivery
        for (FileObject fileObject : savedFiles) {
            fileObject.setBizId(delivery.getId());
            fileObjectRepository.save(fileObject);
        }

        // Update task status to PENDING_CONFIRMATION (for tracking 3-day auto-confirm)
        task.setStatus(TaskStatus.PENDING_CONFIRMATION);
        taskRepository.save(task);

        // Notify publisher
        User publisher = userRepository.findById(task.getPublisherId()).orElse(null);
        User worker = userRepository.findById(workerId).orElse(null);
        String workerName = worker != null ? worker.getNickname() : "接单者";
        if (publisher != null) {
            Notification notification = new Notification();
            notification.setReceiverId(publisher.getId());
            notification.setType(NotificationType.DELIVERY_SUBMITTED);
            notification.setTitle("交付物已提交");
            notification.setContent(workerName + " 已提交任务「" + task.getTitle() + "」的交付物，请及时确认");
            notification.setTargetUrl("/tasks/" + taskId + "/deliveries/" + delivery.getId());
            notificationRepository.save(notification);
        }

        // Record audit log
        AuditLog auditLog = new AuditLog();
        auditLog.setOperatorId(workerId);
        auditLog.setActionType(AuditActionType.DELIVERY_SUBMIT);
        auditLog.setTargetType("TASK");
        auditLog.setTargetId(taskId);
        auditLog.setDetail("接单者提交交付物，任务ID：" + taskId + "，交付物ID：" + delivery.getId()
                + "，历史被退回次数：" + rejectionCount);
        auditLog.setIp("system");
        auditLogRepository.save(auditLog);

        return delivery;
    }

    /**
     * Publisher confirms delivery is complete. Supports both MANUAL and AUTO sources.
     */
    @Transactional
    public void confirmComplete(Long taskId, Long publisherId, String source) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("任务不存在"));

        if ("MANUAL".equals(source)) {
            // Validate publisher
            if (!task.getPublisherId().equals(publisherId)) {
                throw new RuntimeException("只有发布者才能确认完成");
            }
        }

        // Validate task status
        if (task.getStatus() != TaskStatus.PENDING_CONFIRMATION) {
            throw new RuntimeException("任务当前状态不允许确认完成，当前状态：" + task.getStatus().getDisplayName());
        }

        // Idempotency check: if already completed, return silently
        if (task.getStatus() == TaskStatus.COMPLETED) {
            return;
        }

        // Find the latest delivery for this task
        Delivery delivery = deliveryRepository.findFirstByTaskIdOrderBySubmitTimeDesc(taskId)
                .orElseThrow(() -> new RuntimeException("未找到该任务的交付物"));

        if (delivery.getStatus() == DeliveryStatus.ACCEPTED) {
            // Already confirmed, idempotent
            return;
        }

        // Update delivery status
        delivery.setStatus(DeliveryStatus.ACCEPTED);
        deliveryRepository.save(delivery);

        // Perform point transfer
        int rewardPoints = task.getRewardPoints();
        Long workerId = task.getWinnerId();

        // Publisher: decrease frozenPoints
        PointAccount publisherAccount = pointAccountRepository.findByUserIdForUpdate(publisherId)
                .orElseThrow(() -> new RuntimeException("发布者点券账户不存在"));
        if (publisherAccount.getFrozenPoints() < rewardPoints) {
            throw new RuntimeException("发布者冻结点券不足，无法完成转账");
        }
        int publisherFrozenBefore = publisherAccount.getFrozenPoints();
        publisherAccount.setFrozenPoints(publisherAccount.getFrozenPoints() - rewardPoints);
        publisherAccount.setTotalExpense(publisherAccount.getTotalExpense() + rewardPoints);
        pointAccountRepository.save(publisherAccount);

        // Publisher PointFlow (EXPENSE)
        PointFlow publisherFlow = new PointFlow();
        publisherFlow.setUserId(publisherId);
        publisherFlow.setTaskId(taskId);
        publisherFlow.setChangeAmount(-rewardPoints);
        publisherFlow.setBalanceBefore(publisherFrozenBefore);
        publisherFlow.setBalanceAfter(publisherAccount.getFrozenPoints());
        publisherFlow.setFlowType(PointFlowType.EXPENSE);
        publisherFlow.setDescription("任务「" + task.getTitle() + "」完成，点券转账给接单者");
        pointFlowRepository.save(publisherFlow);

        // Worker: increase availablePoints and totalIncome
        PointAccount workerAccount = pointAccountRepository.findByUserIdForUpdate(workerId)
                .orElseThrow(() -> new RuntimeException("接单者点券账户不存在"));
        int workerAvailableBefore = workerAccount.getAvailablePoints();
        workerAccount.setAvailablePoints(workerAccount.getAvailablePoints() + rewardPoints);
        workerAccount.setTotalIncome(workerAccount.getTotalIncome() + rewardPoints);
        pointAccountRepository.save(workerAccount);

        // Worker PointFlow (INCOME)
        PointFlow workerFlow = new PointFlow();
        workerFlow.setUserId(workerId);
        workerFlow.setTaskId(taskId);
        workerFlow.setChangeAmount(rewardPoints);
        workerFlow.setBalanceBefore(workerAvailableBefore);
        workerFlow.setBalanceAfter(workerAccount.getAvailablePoints());
        workerFlow.setFlowType(PointFlowType.INCOME);
        workerFlow.setDescription("完成任务「" + task.getTitle() + "」，获得点券奖励");
        pointFlowRepository.save(workerFlow);

        // Update task status
        task.setStatus(TaskStatus.COMPLETED);
        task.setCompletedAt(LocalDateTime.now());
        taskRepository.save(task);

        // Notify both parties about review invitation
        User publisher = userRepository.findById(publisherId).orElse(null);
        User worker = userRepository.findById(workerId).orElse(null);
        String publisherName = publisher != null ? publisher.getNickname() : "发布者";
        String workerName = worker != null ? worker.getNickname() : "接单者";

        // Notify publisher to review worker
        Notification pubNotification = new Notification();
        pubNotification.setReceiverId(publisherId);
        pubNotification.setType(NotificationType.REVIEW_REQUEST);
        pubNotification.setTitle("请评价接单者");
        pubNotification.setContent("任务「" + task.getTitle() + "」已完成，请对接单者 " + workerName + " 进行评价");
        pubNotification.setTargetUrl("/tasks/" + taskId + "/review");
        notificationRepository.save(pubNotification);

        // Notify worker to review publisher
        Notification workerNotification = new Notification();
        workerNotification.setReceiverId(workerId);
        workerNotification.setType(NotificationType.REVIEW_REQUEST);
        workerNotification.setTitle("请评价发布者");
        workerNotification.setContent("任务「" + task.getTitle() + "」已完成，请对发布者 " + publisherName + " 进行评价");
        workerNotification.setTargetUrl("/tasks/" + taskId + "/review");
        notificationRepository.save(workerNotification);

        // Notify both about task completion
        Notification completionNotice = new Notification();
        completionNotice.setReceiverId(workerId);
        completionNotice.setType(NotificationType.TASK_COMPLETED);
        completionNotice.setTitle("任务已完成");
        completionNotice.setContent("任务「" + task.getTitle() + "」已完成，点券已到账");
        completionNotice.setTargetUrl("/tasks/" + taskId);
        notificationRepository.save(completionNotice);

        // Record audit log
        AuditLog auditLog = new AuditLog();
        auditLog.setOperatorId("AUTO".equals(source) ? null : publisherId);
        auditLog.setActionType("AUTO".equals(source) ? AuditActionType.SYSTEM_AUTO_COMPLETE : AuditActionType.DELIVERY_CONFIRM);
        auditLog.setTargetType("TASK");
        auditLog.setTargetId(taskId);
        auditLog.setDetail("任务确认完成，来源：" + source + "，交付物ID：" + delivery.getId()
                + "，点券转账：" + rewardPoints + " 从用户 " + publisherId + " 到用户 " + workerId);
        auditLog.setIp("system");
        auditLogRepository.save(auditLog);
    }

    /**
     * Publisher rejects a delivery, asking the worker to revise.
     */
    @Transactional
    public void rejectDelivery(Long taskId, Long publisherId, Long deliveryId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("任务不存在"));

        // Validate publisher
        if (!task.getPublisherId().equals(publisherId)) {
            throw new RuntimeException("只有发布者才能退回交付物");
        }

        // Validate task status
        if (task.getStatus() != TaskStatus.PENDING_CONFIRMATION) {
            throw new RuntimeException("任务当前状态不允许退回交付物，当前状态：" + task.getStatus().getDisplayName());
        }

        // Find delivery
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new RuntimeException("交付物不存在"));

        if (!delivery.getTaskId().equals(taskId)) {
            throw new RuntimeException("交付物不属于该任务");
        }

        if (delivery.getStatus() == DeliveryStatus.REJECTED) {
            throw new RuntimeException("该交付物已被退回，无需重复操作");
        }

        // Update delivery status
        delivery.setStatus(DeliveryStatus.REJECTED);
        delivery.setRejectedAt(LocalDateTime.now());
        delivery.setRejectCount(delivery.getRejectCount() != null ? delivery.getRejectCount() + 1 : 1);
        deliveryRepository.save(delivery);

        // Update task status back to IN_PROGRESS
        task.setStatus(TaskStatus.IN_PROGRESS);
        taskRepository.save(task);

        // Notify worker about rejection
        Long workerId = delivery.getWorkerId();
        User worker = userRepository.findById(workerId).orElse(null);
        String workerName = worker != null ? worker.getNickname() : "接单者";
        String rejectionMessage = "任务「" + task.getTitle() + "」的交付物已被退回，请修改后重新提交。";
        int rejectCount = delivery.getRejectCount();
        if (rejectCount >= 3) {
            rejectionMessage += " 注意：已连续被退回 " + rejectCount + " 次，发布者可申请平台介入处理。";
        }

        Notification notification = new Notification();
        notification.setReceiverId(workerId);
        notification.setType(NotificationType.DELIVERY_REJECTED);
        notification.setTitle("交付物被退回");
        notification.setContent(rejectionMessage);
        notification.setTargetUrl("/tasks/" + taskId + "/deliveries");
        notificationRepository.save(notification);

        // Record audit log
        AuditLog auditLog = new AuditLog();
        auditLog.setOperatorId(publisherId);
        auditLog.setActionType(AuditActionType.DELIVERY_REJECT);
        auditLog.setTargetType("DELIVERY");
        auditLog.setTargetId(deliveryId);
        auditLog.setDetail("发布者退回交付物，任务ID：" + taskId + "，交付物ID：" + deliveryId
                + "，累计退回次数：" + rejectCount
                + (rejectCount >= 3 ? "（已达3次，发布者可申诉）" : ""));
        auditLog.setIp("system");
        auditLogRepository.save(auditLog);
    }

    /**
     * Get a specific delivery for a task. Only the publisher or worker can view.
     */
    public Delivery getDeliveryForTask(Long taskId, Long userId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("任务不存在"));

        // Authorization: only publisher or winner can view
        if (!task.getPublisherId().equals(userId) &&
                (task.getWinnerId() == null || !task.getWinnerId().equals(userId))) {
            throw new RuntimeException("无权限查看该任务的交付物");
        }

        Delivery delivery = deliveryRepository.findFirstByTaskIdOrderBySubmitTimeDesc(taskId)
                .orElseThrow(() -> new RuntimeException("未找到该任务的交付物"));

        // Load associated files
        List<FileObject> files = fileObjectRepository.findByBizTypeAndBizId(
                FileObject.BIZ_TYPE_DELIVERY_ATTACHMENT, delivery.getId());
        // Attach file list info (we return the delivery entity; caller can use fileRepository to get files)
        // The files are implicitly accessible through the deliveryId

        return delivery;
    }

    /**
     * Get all deliveries for a task (history of submissions).
     */
    public List<Delivery> getDeliveriesForTask(Long taskId, Long userId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("任务不存在"));

        // Authorization: only publisher or winner can view
        if (!task.getPublisherId().equals(userId) &&
                (task.getWinnerId() == null || !task.getWinnerId().equals(userId))) {
            throw new RuntimeException("无权限查看该任务的交付记录");
        }

        return deliveryRepository.findByTaskIdOrderBySubmitTimeDesc(taskId);
    }

    /**
     * Get files associated with a delivery.
     */
    public List<FileObject> getDeliveryFiles(Long deliveryId) {
        return fileObjectRepository.findByBizTypeAndBizId(
                FileObject.BIZ_TYPE_DELIVERY_ATTACHMENT, deliveryId);
    }

    /**
     * Simple file save logic - saves MultipartFile to local disk and creates FileObject record.
     */
    private FileObject saveFile(MultipartFile file, Long taskId) throws IOException {
        String originalName = file.getOriginalFilename();
        String extension = "";
        if (originalName != null && originalName.contains(".")) {
            extension = originalName.substring(originalName.lastIndexOf("."));
        }
        String storedName = UUID.randomUUID().toString() + extension;

        Path uploadPath = Paths.get(UPLOAD_DIR);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        Path filePath = uploadPath.resolve(storedName);
        file.transferTo(filePath.toFile());

        String fileUrl = "/uploads/deliveries/" + storedName;

        FileObject fileObject = new FileObject();
        fileObject.setBizType(FileObject.BIZ_TYPE_DELIVERY_ATTACHMENT);
        fileObject.setBizId(0L); // Will be updated after delivery is saved
        fileObject.setOriginalName(originalName);
        fileObject.setStoredName(storedName);
        fileObject.setFileUrl(fileUrl);
        fileObject.setFileSize(file.getSize());
        fileObject.setContentType(file.getContentType());

        return fileObjectRepository.save(fileObject);
    }
}
