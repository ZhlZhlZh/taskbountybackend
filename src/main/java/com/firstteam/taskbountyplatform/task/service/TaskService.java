package com.firstteam.taskbountyplatform.task.service;

import com.firstteam.taskbountyplatform.common.enums.ApplicationStatus;
import com.firstteam.taskbountyplatform.common.enums.AuditActionType;
import com.firstteam.taskbountyplatform.audit.service.AuditLogService;
import com.firstteam.taskbountyplatform.auth.security.UserContext;
import com.firstteam.taskbountyplatform.common.enums.CampusEnum;
import com.firstteam.taskbountyplatform.common.exception.BusinessException;
import com.firstteam.taskbountyplatform.common.response.PageResult;
import com.firstteam.taskbountyplatform.config.PlatformConfig;
import com.firstteam.taskbountyplatform.file.dto.FileDTO;
import com.firstteam.taskbountyplatform.file.entity.FileObject;
import com.firstteam.taskbountyplatform.file.service.FileService;
import com.firstteam.taskbountyplatform.common.enums.NotificationType;
import com.firstteam.taskbountyplatform.notification.service.NotificationService;
import com.firstteam.taskbountyplatform.point.entity.PointAccount;
import com.firstteam.taskbountyplatform.point.entity.PointFlow;
import com.firstteam.taskbountyplatform.common.enums.PointFlowType;
import com.firstteam.taskbountyplatform.point.repository.PointAccountRepository;
import com.firstteam.taskbountyplatform.point.repository.PointFlowRepository;
import com.firstteam.taskbountyplatform.common.enums.TaskStatus;
import com.firstteam.taskbountyplatform.task.dto.*;
import com.firstteam.taskbountyplatform.task.entity.*;
import com.firstteam.taskbountyplatform.task.repository.*;
import com.firstteam.taskbountyplatform.user.entity.User;
import com.firstteam.taskbountyplatform.user.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TaskService - core business logic for task publishing, browsing, applying,
 * awarding, messaging, extending deadlines, and appeals.
 */
@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    // ========== Sensitive word filter constants ==========
    private static final List<String> BANNED_WORDS = List.of(
            "色情", "赌博", "毒品", "枪支", "暴力", "诈骗",
            "代考", "作弊", "刷单", "裸聊", "反动", "传销",
            "办证", "卖淫", "嫖娼", "贩毒", "走私", "洗钱"
    );

    // ========== Rate limit / violation tracking ==========
    // Key: "taskId:senderId", Value: list of message send timestamps
    private final ConcurrentHashMap<String, List<LocalDateTime>> messageRateLimiter = new ConcurrentHashMap<>();
    // Key: userId, Value: list of violation timestamps
    private final ConcurrentHashMap<Long, List<LocalDateTime>> violationTracker = new ConcurrentHashMap<>();

    // ========== Dependencies ==========
    private final TaskRepository taskRepository;
    private final TaskCategoryRepository categoryRepository;
    private final TaskApplicationRepository applicationRepository;
    private final MessageBoardRepository messageRepository;
    private final TaskAppealRepository appealRepository;
    private final UserRepository userRepository;
    private final PointAccountRepository pointAccountRepository;
    private final PointFlowRepository pointFlowRepository;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final FileService fileService;
    private final UserContext userContext;
    private final PlatformConfig platformConfig;

    public TaskService(TaskRepository taskRepository,
                       TaskCategoryRepository categoryRepository,
                       TaskApplicationRepository applicationRepository,
                       MessageBoardRepository messageRepository,
                       TaskAppealRepository appealRepository,
                       UserRepository userRepository,
                       PointAccountRepository pointAccountRepository,
                       PointFlowRepository pointFlowRepository,
                       NotificationService notificationService,
                       AuditLogService auditLogService,
                       FileService fileService,
                       UserContext userContext,
                       PlatformConfig platformConfig) {
        this.taskRepository = taskRepository;
        this.categoryRepository = categoryRepository;
        this.applicationRepository = applicationRepository;
        this.messageRepository = messageRepository;
        this.appealRepository = appealRepository;
        this.userRepository = userRepository;
        this.pointAccountRepository = pointAccountRepository;
        this.pointFlowRepository = pointFlowRepository;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
        this.fileService = fileService;
        this.userContext = userContext;
        this.platformConfig = platformConfig;
    }

    // ========================================================================
    // 1. publishTask
    // ========================================================================

    @Transactional
    public TaskDTO publishTask(Long publisherId, TaskCreateRequest request) {
        // Validate publisher exists
        User publisher = userRepository.findById(publisherId)
                .orElseThrow(() -> new BusinessException(404, "发布者不存在"));

        // Validate category exists and is enabled
        TaskCategory category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new BusinessException(404, "任务分类不存在"));
        if (!Boolean.TRUE.equals(category.getEnabled())) {
            throw new BusinessException(400, "该分类已被禁用");
        }

        // Validate publisher has enough available points (lock point account)
        PointAccount account = pointAccountRepository.findByUserIdForUpdate(publisherId)
                .orElseThrow(() -> new BusinessException(400, "积分账户不存在"));

        int rewardPoints = request.getRewardPoints();
        if (account.getAvailablePoints() < rewardPoints) {
            throw new BusinessException(400, "可用积分不足，需要 " + rewardPoints + " 积分，当前可用 " + account.getAvailablePoints());
        }

        // Freeze reward points
        int balanceBefore = account.getAvailablePoints();
        account.setAvailablePoints(account.getAvailablePoints() - rewardPoints);
        account.setFrozenPoints(account.getFrozenPoints() + rewardPoints);
        pointAccountRepository.save(account);

        // Record point flow - FREEZE
        PointFlow freezeFlow = new PointFlow();
        freezeFlow.setUserId(publisherId);
        freezeFlow.setChangeAmount(-rewardPoints);
        freezeFlow.setBalanceBefore(balanceBefore);
        freezeFlow.setBalanceAfter(account.getAvailablePoints());
        freezeFlow.setFlowType(PointFlowType.FREEZE);
        freezeFlow.setDescription("发布任务冻结积分: " + request.getTitle());
        pointFlowRepository.save(freezeFlow);

        // Create Task entity
        Task task = new Task();
        task.setPublisherId(publisherId);
        task.setCategoryId(category.getId());
        task.setCategoryName(category.getName());
        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());

        // Parse campus
        if (request.getCampus() != null && !request.getCampus().isBlank()) {
            try {
                task.setCampus(CampusEnum.valueOf(request.getCampus()));
            } catch (IllegalArgumentException e) {
                throw new BusinessException(400, "无效的校区: " + request.getCampus());
            }
        } else {
            task.setCampus(CampusEnum.BOTH);
        }

        task.setRewardPoints(rewardPoints);
        task.setDeadlineMinutes(request.getDeadlineMinutes() != null ? request.getDeadlineMinutes() : 60);
        task.setAutoCancelDays(request.getAutoCancelDays() != null ? request.getAutoCancelDays() : platformConfig.getTask().getAutoCancelDays());
        task.setStatus(TaskStatus.PUBLISHED);
        task.setPublishedAt(LocalDateTime.now());
        task = taskRepository.save(task);

        // Record audit log
        auditLogService.log(publisherId, AuditActionType.TASK_PUBLISH,
                "TASK", task.getId(), "发布任务: " + task.getTitle(), "127.0.0.1");

        log.info("Task published: id={}, publisherId={}, title={}, reward={}",
                task.getId(), publisherId, task.getTitle(), rewardPoints);

        return convertToTaskDTO(task, publisher);
    }

    // ========================================================================
    // 2. browseTasks
    // ========================================================================

    @Transactional(readOnly = true)
    public PageResult<TaskCardDTO> browseTasks(TaskSearchRequest request) {
        // Calculate cutoff: tasks within autoCancelDays are visible
        int autoCancelDays = platformConfig.getTask().getAutoCancelDays();
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(autoCancelDays);

        Specification<Task> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Only PUBLISHED tasks within autoCancelDays
            predicates.add(cb.equal(root.get("status"), TaskStatus.PUBLISHED));
            predicates.add(cb.greaterThanOrEqualTo(root.get("publishedAt"), cutoffDate));

            // Keyword search on title
            if (request.getKeyword() != null && !request.getKeyword().isBlank()) {
                predicates.add(cb.like(root.get("title"), "%" + request.getKeyword() + "%"));
            }

            // Category filter
            if (request.getCategoryIds() != null && !request.getCategoryIds().isEmpty()) {
                predicates.add(root.get("categoryId").in(request.getCategoryIds()));
            }

            // Reward range filter
            if (request.getMinReward() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("rewardPoints"), request.getMinReward()));
            }
            if (request.getMaxReward() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("rewardPoints"), request.getMaxReward()));
            }

            // Date range filter
            if (request.getStartDate() != null) {
                LocalDateTime startDateTime = request.getStartDate().atStartOfDay();
                predicates.add(cb.greaterThanOrEqualTo(root.get("publishedAt"), startDateTime));
            }
            if (request.getEndDate() != null) {
                LocalDateTime endDateTime = request.getEndDate().atTime(LocalTime.MAX);
                predicates.add(cb.lessThanOrEqualTo(root.get("publishedAt"), endDateTime));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        // Build sort
        Sort sort;
        String sortBy = request.getSortBy();
        if ("reward".equals(sortBy)) {
            sort = Sort.by(Sort.Direction.DESC, "rewardPoints");
        } else {
            // Default: "time" or null, sort by publishedAt DESC
            sort = Sort.by(Sort.Direction.DESC, "publishedAt");
        }

        Pageable pageable = PageRequest.of(
                Math.max(0, request.getPage() - 1),
                Math.max(1, Math.min(request.getSize(), 50)),
                sort);

        Page<Task> page = taskRepository.findAll(spec, pageable);

        List<TaskCardDTO> cards = page.getContent().stream()
                .map(this::convertToTaskCardDTO)
                .toList();

        return new PageResult<>(cards, request.getPage(), request.getSize(), page.getTotalElements());
    }

    // ========================================================================
    // 3. getTaskDetail
    // ========================================================================

    @Transactional(readOnly = true)
    public TaskDTO getTaskDetail(Long taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(404, "任务不存在"));

        User publisher = userRepository.findById(task.getPublisherId())
                .orElse(null);

        TaskDTO dto = convertToTaskDTO(task, publisher);

        // Get task files
        List<FileObject> files = fileService.getFilesByBizType(
                FileObject.BIZ_TYPE_TASK_ATTACHMENT, taskId);
        if (files != null && !files.isEmpty()) {
            dto.setFiles(files.stream().map(f -> {
                FileDTO fd = new FileDTO();
                fd.setId(f.getId());
                fd.setOriginalName(f.getOriginalName());
                fd.setFileUrl(f.getFileUrl());
                fd.setFileSize(f.getFileSize());
                fd.setContentType(f.getContentType());
                fd.setCreatedAt(f.getCreatedAt());
                return fd;
            }).toList());
        }

        // Get application count (public info)
        long applicationCount = applicationRepository.findByTaskIdOrderByApplicantIdAsc(taskId).size();
        dto.setApplicationCount((int) applicationCount);

        // Check if current user is the publisher (for publisher-only info)
        Long currentUserId = getCurrentUserIdOrNull();
        if (currentUserId != null && currentUserId.equals(task.getPublisherId())) {
            // Publisher can see application list
            List<TaskApplication> apps = applicationRepository.findByTaskIdOrderByApplicantIdAsc(taskId);
            List<ApplicationDTO> appDTOs = apps.stream()
                    .map(app -> convertToApplicationDTO(app))
                    .toList();
            dto.setApplications(appDTOs);
        }

        // Check if current user applied (show their application status)
        if (currentUserId != null && !currentUserId.equals(task.getPublisherId())) {
            applicationRepository.findByTaskIdAndApplicantId(taskId, currentUserId)
                    .ifPresent(app -> dto.setMyApplicationStatus(app.getStatus().name()));
        }

        // Set winner nickname if exists
        if (task.getWinnerId() != null) {
            userRepository.findById(task.getWinnerId())
                    .ifPresent(winner -> dto.setWinnerNickname(winner.getNickname()));
        }

        return dto;
    }

    // ========================================================================
    // 4. updateTask
    // ========================================================================

    @Transactional
    public TaskDTO updateTask(Long taskId, Long publisherId, TaskUpdateRequest request) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(404, "任务不存在"));

        // Only publisher can update
        if (!task.getPublisherId().equals(publisherId)) {
            throw new BusinessException(403, "只有发布者可以编辑任务");
        }

        // Only if task is PUBLISHED and has no applications
        if (task.getStatus() != TaskStatus.PUBLISHED) {
            throw new BusinessException(400, "只有发布中的任务可以编辑");
        }
        List<TaskApplication> existingApps = applicationRepository.findByTaskIdOrderByApplicantIdAsc(taskId);
        if (!existingApps.isEmpty()) {
            throw new BusinessException(400, "已有申请的任务不能编辑");
        }

        // If reward changed, adjust frozen points (difference)
        if (request.getRewardPoints() != null && !request.getRewardPoints().equals(task.getRewardPoints())) {
            int oldReward = task.getRewardPoints();
            int newReward = request.getRewardPoints();
            int diff = newReward - oldReward;

            PointAccount account = pointAccountRepository.findByUserIdForUpdate(publisherId)
                    .orElseThrow(() -> new BusinessException(400, "积分账户不存在"));

            if (diff > 0) {
                // Need more points frozen
                if (account.getAvailablePoints() < diff) {
                    throw new BusinessException(400, "可用积分不足，无法增加赏金");
                }
                account.setAvailablePoints(account.getAvailablePoints() - diff);
                account.setFrozenPoints(account.getFrozenPoints() + diff);

                PointFlow flow = new PointFlow();
                flow.setUserId(publisherId);
                flow.setTaskId(taskId);
                flow.setChangeAmount(-diff);
                flow.setBalanceBefore(account.getAvailablePoints() + diff);
                flow.setBalanceAfter(account.getAvailablePoints());
                flow.setFlowType(PointFlowType.FREEZE);
                flow.setDescription("任务编辑增加赏金: " + diff + " 积分");
                pointFlowRepository.save(flow);
            } else if (diff < 0) {
                // Return some frozen points
                int returnAmount = -diff;
                account.setAvailablePoints(account.getAvailablePoints() + returnAmount);
                account.setFrozenPoints(account.getFrozenPoints() - returnAmount);

                PointFlow flow = new PointFlow();
                flow.setUserId(publisherId);
                flow.setTaskId(taskId);
                flow.setChangeAmount(returnAmount);
                flow.setBalanceBefore(account.getAvailablePoints() - returnAmount);
                flow.setBalanceAfter(account.getAvailablePoints());
                flow.setFlowType(PointFlowType.UNFREEZE);
                flow.setDescription("任务编辑减少赏金，返还: " + returnAmount + " 积分");
                pointFlowRepository.save(flow);
            }
            pointAccountRepository.save(account);
            task.setRewardPoints(newReward);
        }

        // Update editable fields
        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            task.setTitle(request.getTitle());
        }
        if (request.getDescription() != null && !request.getDescription().isBlank()) {
            task.setDescription(request.getDescription());
        }
        if (request.getCategoryId() != null) {
            TaskCategory category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new BusinessException(404, "分类不存在"));
            task.setCategoryId(category.getId());
            task.setCategoryName(category.getName());
        }
        if (request.getCampus() != null && !request.getCampus().isBlank()) {
            try {
                task.setCampus(CampusEnum.valueOf(request.getCampus()));
            } catch (IllegalArgumentException e) {
                throw new BusinessException(400, "无效的校区: " + request.getCampus());
            }
        }
        if (request.getDeadlineMinutes() != null) {
            task.setDeadlineMinutes(request.getDeadlineMinutes());
        }

        task = taskRepository.save(task);

        auditLogService.log(publisherId, AuditActionType.TASK_PUBLISH,
                "TASK", taskId, "编辑任务: " + task.getTitle(), "127.0.0.1");

        User publisher = userRepository.findById(publisherId).orElse(null);
        return convertToTaskDTO(task, publisher);
    }

    // ========================================================================
    // 5. cancelTask
    // ========================================================================

    @Transactional
    public void cancelTask(Long taskId, Long publisherId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(404, "任务不存在"));

        // Only publisher can cancel
        if (!task.getPublisherId().equals(publisherId)) {
            throw new BusinessException(403, "只有发布者可以取消任务");
        }

        // Only if task status is PUBLISHED
        if (task.getStatus() != TaskStatus.PUBLISHED) {
            throw new BusinessException(400, "只有发布中的任务可以取消");
        }

        int rewardPoints = task.getRewardPoints();

        // Penalty: 5% of reward points deducted from frozen, rest returned
        int penaltyPoints = (int) Math.ceil(rewardPoints * 0.05);
        int returnPoints = rewardPoints - penaltyPoints;

        PointAccount account = pointAccountRepository.findByUserIdForUpdate(publisherId)
                .orElseThrow(() -> new BusinessException(400, "积分账户不存在"));

        int balanceBefore = account.getAvailablePoints();

        // Unfreeze total reward
        account.setFrozenPoints(account.getFrozenPoints() - rewardPoints);

        // Return points minus penalty
        if (returnPoints > 0) {
            account.setAvailablePoints(account.getAvailablePoints() + returnPoints);

            PointFlow returnFlow = new PointFlow();
            returnFlow.setUserId(publisherId);
            returnFlow.setTaskId(taskId);
            returnFlow.setChangeAmount(returnPoints);
            returnFlow.setBalanceBefore(balanceBefore);
            returnFlow.setBalanceAfter(account.getAvailablePoints());
            returnFlow.setFlowType(PointFlowType.UNFREEZE);
            returnFlow.setDescription("取消任务返还积分(扣5%违约金): " + returnPoints + " 积分");
            pointFlowRepository.save(returnFlow);

            // Record the penalty as expense
            if (penaltyPoints > 0) {
                PointFlow penaltyFlow = new PointFlow();
                penaltyFlow.setUserId(publisherId);
                penaltyFlow.setTaskId(taskId);
                penaltyFlow.setChangeAmount(-penaltyPoints);
                penaltyFlow.setBalanceBefore(account.getAvailablePoints());
                penaltyFlow.setBalanceAfter(account.getAvailablePoints());
                penaltyFlow.setFlowType(PointFlowType.EXPENSE);
                penaltyFlow.setDescription("取消任务违约金: " + penaltyPoints + " 积分");
                pointFlowRepository.save(penaltyFlow);
            }
        }
        pointAccountRepository.save(account);

        // Change task status to CANCELLED
        task.setStatus(TaskStatus.CANCELLED);
        task.setCancelledAt(LocalDateTime.now());
        taskRepository.save(task);

        // Credit score penalty (-5)
        User publisher = userRepository.findById(publisherId).orElse(null);
        if (publisher != null) {
            int oldScore = publisher.getCreditScore() != null ? publisher.getCreditScore() : 80;
            int newScore = Math.max(0, oldScore + platformConfig.getCredit().getPublisherCancelPenalty());
            publisher.setCreditScore(newScore);
            userRepository.save(publisher);
        }

        // Record audit log
        auditLogService.log(publisherId, AuditActionType.TASK_PUBLISH,
                "TASK", taskId, "取消任务: " + task.getTitle() + ", 扣违约金 " + penaltyPoints + " 积分", "127.0.0.1");

        log.info("Task cancelled: id={}, publisherId={}, penaltyPoints={}", taskId, publisherId, penaltyPoints);
    }

    // ========================================================================
    // 6. applyForTask
    // ========================================================================

    @Transactional
    public ApplicationDTO applyForTask(Long taskId, Long applicantId, TaskApplyRequest request) {
        // Validate applicant exists and credit score >= 40
        User applicant = userRepository.findById(applicantId)
                .orElseThrow(() -> new BusinessException(404, "申请人不存在"));

        if (applicant.getCreditScore() != null && applicant.getCreditScore() < platformConfig.getCredit().getRestrictThreshold()) {
            throw new BusinessException(400, "信用分不足，当前信用分: " + applicant.getCreditScore() + "，需要至少 " + platformConfig.getCredit().getRestrictThreshold());
        }

        // Get task
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(404, "任务不存在"));

        // Validate not applying for own task
        if (task.getPublisherId().equals(applicantId)) {
            throw new BusinessException(400, "不能申请自己发布的任务");
        }

        // Validate task is PUBLISHED
        if (task.getStatus() != TaskStatus.PUBLISHED) {
            throw new BusinessException(400, "该任务当前不可申请，状态: " + task.getStatus().name());
        }

        // Validate not already applied
        if (applicationRepository.existsByTaskIdAndApplicantId(taskId, applicantId)) {
            throw new BusinessException(400, "您已申请过该任务");
        }

        // Check concurrent orders limit (max 3 active awarded applications)
        long activeOrders = applicationRepository.countActiveOrders(applicantId);
        int maxConcurrent = platformConfig.getMaxConcurrentOrders();
        if (activeOrders >= maxConcurrent) {
            throw new BusinessException(400, "当前进行中的订单已达上限(" + maxConcurrent + "个)，无法申请新任务");
        }

        // Create TaskApplication
        TaskApplication application = new TaskApplication();
        application.setTaskId(taskId);
        application.setApplicantId(applicantId);
        application.setApplyReason(request.getApplyReason());
        application.setStatus(ApplicationStatus.REVIEWING);
        applicationRepository.save(application);

        // Notify task publisher
        notificationService.createNotification(
                task.getPublisherId(),
                NotificationType.TASK_UPDATE,
                "新的任务申请",
                applicant.getNickname() + " 申请了您的任务「" + task.getTitle() + "」",
                "/tasks/" + taskId);

        // Record audit log
        auditLogService.log(applicantId, AuditActionType.TASK_APPLY,
                "TASK_APPLICATION", application.getId(), "申请任务: " + task.getTitle(), "127.0.0.1");

        log.info("Application submitted: taskId={}, applicantId={}, appId={}", taskId, applicantId, application.getId());

        return convertToApplicationDTO(application);
    }

    // ========================================================================
    // 7. awardApplication
    // ========================================================================

    @Transactional
    public TaskDTO awardApplication(Long taskId, Long applicationId, Long publisherId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(404, "任务不存在"));

        // Validate task is PUBLISHED and publisher matches
        if (task.getStatus() != TaskStatus.PUBLISHED) {
            throw new BusinessException(400, "只有发布中的任务可以选择中标者");
        }
        if (!task.getPublisherId().equals(publisherId)) {
            throw new BusinessException(403, "只有任务发布者可以选择中标者");
        }

        TaskApplication targetApp = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new BusinessException(404, "申请不存在"));

        // Validate application belongs to this task
        if (!targetApp.getTaskId().equals(taskId)) {
            throw new BusinessException(400, "该申请不属于此任务");
        }

        // Validate application is REVIEWING
        if (targetApp.getStatus() != ApplicationStatus.REVIEWING) {
            throw new BusinessException(400, "只能选择审核中的申请");
        }

        Long winnerId = targetApp.getApplicantId();

        // Validate applicant's concurrent orders < max (3)
        long activeOrders = applicationRepository.countActiveOrders(winnerId);
        int maxConcurrent = platformConfig.getMaxConcurrentOrders();
        if (activeOrders >= maxConcurrent) {
            throw new BusinessException(400, "该申请者的进行中订单已达上限(" + maxConcurrent + "个)");
        }

        // Update task: status=IN_PROGRESS, winnerId, awardedAt, deadlineAt
        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setWinnerId(winnerId);
        LocalDateTime now = LocalDateTime.now();
        task.setAwardedAt(now);
        // deadlineAt = awardedAt + deadlineMinutes
        task.setDeadlineAt(now.plusMinutes(task.getDeadlineMinutes()));
        taskRepository.save(task);

        // Update target application: APPROVED (equivalent to AWARDED)
        targetApp.setStatus(ApplicationStatus.AWARDED);
        targetApp.setUpdatedAt(now);
        applicationRepository.save(targetApp);

        // Update all other applications: REJECTED
        List<TaskApplication> allApps = applicationRepository.findByTaskIdOrderByApplicantIdAsc(taskId);
        List<Long> rejectedUserIds = new ArrayList<>();
        for (TaskApplication app : allApps) {
            if (!app.getId().equals(applicationId)) {
                app.setStatus(ApplicationStatus.REJECTED);
                app.setUpdatedAt(now);
                applicationRepository.save(app);
                rejectedUserIds.add(app.getApplicantId());
            }
        }

        // Occupy applicant's order slot (already done by saving APPROVED status)

        // Send notifications to winner
        User winner = userRepository.findById(winnerId).orElse(null);
        String winnerName = winner != null ? winner.getNickname() : "用户" + winnerId;
        notificationService.createNotification(
                winnerId,
                NotificationType.TASK_AWARDED,
                "恭喜中标",
                "您已中标任务「" + task.getTitle() + "」，请尽快完成交付",
                "/tasks/" + taskId);

        // Send notifications to rejected applicants
        if (!rejectedUserIds.isEmpty()) {
            notificationService.createBatchNotifications(
                    rejectedUserIds,
                    NotificationType.TASK_REJECTED,
                    "申请结果通知",
                    "很遗憾，您在任务「" + task.getTitle() + "」中的申请未中标",
                    "/tasks/" + taskId);
        }

        // Record audit log
        auditLogService.log(publisherId, AuditActionType.TASK_AWARD,
                "TASK", taskId, "选择中标者: " + winnerName + " (申请ID: " + applicationId + ")", "127.0.0.1");

        log.info("Application awarded: taskId={}, appId={}, winnerId={}", taskId, applicationId, winnerId);

        User publisher = userRepository.findById(publisherId).orElse(null);
        return convertToTaskDTO(task, publisher);
    }

    // ========================================================================
    // 8. getApplicationsForTask
    // ========================================================================

    @Transactional(readOnly = true)
    public List<ApplicationDTO> getApplicationsForTask(Long taskId, Long publisherId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(404, "任务不存在"));

        // Only the publisher of the task can view applications
        if (!task.getPublisherId().equals(publisherId)) {
            throw new BusinessException(403, "只有任务发布者可以查看申请列表");
        }

        List<TaskApplication> apps = applicationRepository.findByTaskIdOrderByApplicantIdAsc(taskId);
        return apps.stream()
                .map(this::convertToApplicationDTO)
                .toList();
    }

    // ========================================================================
    // 9. getMyApplications
    // ========================================================================

    @Transactional(readOnly = true)
    public PageResult<MyApplicationDTO> getMyApplications(Long userId, String status, Pageable pageable) {
        // Use Specification to filter by status to avoid enum type mismatch
        // (entity uses task.entity.ApplicationStatus, repository uses common.enums.ApplicationStatus)
        Page<TaskApplication> page;
        if (status != null && !status.isBlank()) {
            // Validate status string
            try {
                ApplicationStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BusinessException(400, "无效的申请状态: " + status);
            }
            Specification<TaskApplication> spec = (root, query, cb) -> {
                return cb.and(
                        cb.equal(root.get("applicantId"), userId),
                        cb.equal(root.get("status"), ApplicationStatus.valueOf(status.toUpperCase()))
                );
            };
            page = applicationRepository.findAll(spec, PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                    Sort.by(Sort.Direction.DESC, "appliedAt")));
        } else {
            page = applicationRepository.findByApplicantIdOrderByAppliedAtDesc(userId, pageable);
        }

        List<MyApplicationDTO> dtos = page.getContent().stream()
                .map(app -> convertToMyApplicationDTO(app))
                .toList();

        return new PageResult<>(dtos, page.getNumber() + 1, page.getSize(), page.getTotalElements());
    }

    // ========================================================================
    // 10. getMyTasks
    // ========================================================================

    @Transactional(readOnly = true)
    public PageResult<TaskCardDTO> getMyTasks(Long userId, String status, Pageable pageable) {
        // Build sort: publishedAt desc
        Sort sort = Sort.by(Sort.Direction.DESC, "publishedAt");
        Pageable sortedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);

        Page<Task> page;
        if (status != null && !status.isBlank()) {
            // Validate status string
            try {
                TaskStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BusinessException(400, "无效的任务状态: " + status);
            }
            Specification<Task> spec = (root, query, cb) -> {
                return cb.and(
                        cb.equal(root.get("publisherId"), userId),
                        cb.equal(root.get("status"), TaskStatus.valueOf(status.toUpperCase()))
                );
            };
            page = taskRepository.findAll(spec, sortedPageable);
        } else {
            page = taskRepository.findByPublisherIdOrderByPublishedAtDesc(userId, sortedPageable);
        }

        List<TaskCardDTO> cards = page.getContent().stream()
                .map(this::convertToTaskCardDTO)
                .toList();

        return new PageResult<>(cards, page.getNumber() + 1, page.getSize(), page.getTotalElements());
    }

    // ========================================================================
    // 11. getTaskMessages
    // ========================================================================

    @Transactional(readOnly = true)
    public List<MessageDTO> getTaskMessages(Long taskId) {
        List<MessageBoard> messages = messageRepository.findByTaskIdOrderBySentAtAsc(taskId);
        return messages.stream()
                .map(this::convertToMessageDTO)
                .toList();
    }

    // ========================================================================
    // 12. sendMessage
    // ========================================================================

    @Transactional
    public MessageDTO sendMessage(Long taskId, Long senderId, MessageSendRequest request) {
        // Validate task exists and is in allowed status
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(404, "任务不存在"));

        // Task must be IN_PROGRESS or APPEALING (APPEAL)
        if (task.getStatus() != TaskStatus.IN_PROGRESS && task.getStatus() != TaskStatus.APPEALING) {
            throw new BusinessException(400, "当前任务状态不允许发送消息");
        }

        String content = request.getContent();

        // Validate content length (< 50 chars)
        if (content.length() > 50) {
            throw new BusinessException(400, "消息内容不能超过50个字符");
        }

        // Check rate limit: max 5 messages per minute per user per task
        String rateLimitKey = taskId + ":" + senderId;
        LocalDateTime now = LocalDateTime.now();
        List<LocalDateTime> messageTimes = messageRateLimiter.computeIfAbsent(rateLimitKey, k -> new ArrayList<>());

        // Clean up old entries (older than 1 minute)
        messageTimes.removeIf(time -> time.isBefore(now.minusMinutes(1)));

        if (messageTimes.size() >= 5) {
            throw new BusinessException(429, "发送频率过高，请稍后再试（每分钟最多5条）");
        }
        messageTimes.add(now);

        // Run sensitive word filter
        int violationCount = checkSensitiveWords(content, senderId);

        // Get sender name
        User sender = userRepository.findById(senderId)
                .orElse(null);
        String senderName = sender != null ? sender.getNickname() : "用户" + senderId;

        // Save message
        MessageBoard message = new MessageBoard();
        message.setTaskId(taskId);
        message.setSenderId(senderId);
        message.setSenderName(senderName);
        message.setContent(content);
        message.setSentAt(now);
        messageRepository.save(message);

        // Notify the other party
        Long notifyTargetId = task.getPublisherId().equals(senderId) ? task.getWinnerId() : task.getPublisherId();
        if (notifyTargetId != null) {
            notificationService.createNotification(
                    notifyTargetId,
                    NotificationType.TASK_UPDATE,
                    "新消息",
                    senderName + " 在任务「" + task.getTitle() + "」中发送了新消息",
                    "/tasks/" + taskId);
        }

        log.info("Message sent: taskId={}, senderId={}, violationCount={}", taskId, senderId, violationCount);
        return convertToMessageDTO(message);
    }

    // ========================================================================
    // 13. extendDeadline
    // ========================================================================

    @Transactional
    public TaskDTO extendDeadline(Long taskId, Long publisherId, TaskExtendRequest request) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(404, "任务不存在"));

        // Only publisher can extend
        if (!task.getPublisherId().equals(publisherId)) {
            throw new BusinessException(403, "只有发布者可以延长截止时间");
        }

        // Task must be IN_PROGRESS
        if (task.getStatus() != TaskStatus.IN_PROGRESS) {
            throw new BusinessException(400, "只有进行中的任务可以延长截止时间");
        }

        // Extend count < maxExtendTimes
        int maxExtendTimes = platformConfig.getTask().getMaxExtendTimes();
        if (task.getExtendCount() >= maxExtendTimes) {
            throw new BusinessException(400, "延长次数已达上限(" + maxExtendTimes + "次)");
        }

        // New extend amount <= current remaining time * 50%
        if (task.getDeadlineAt() == null) {
            throw new BusinessException(400, "任务截止时间未设置");
        }
        long remainingMinutes = ChronoUnit.MINUTES.between(LocalDateTime.now(), task.getDeadlineAt());
        if (remainingMinutes <= 0) {
            throw new BusinessException(400, "任务已截止，无法延长");
        }
        long maxSingleExtend = remainingMinutes * platformConfig.getTask().getExtendRatio() / 100;
        int extendMinutes = request.getExtendMinutes();
        if (extendMinutes > maxSingleExtend) {
            throw new BusinessException(400, "单次延长不能超过剩余时间的" + platformConfig.getTask().getExtendRatio() + "% (" + maxSingleExtend + "分钟)");
        }

        // Total extend <= original deadlineMinutes * 2
        int maxTotalExtend = task.getDeadlineMinutes() * 2;
        if (task.getExtendTotalMinutes() + extendMinutes > maxTotalExtend) {
            throw new BusinessException(400, "累计延长不能超过原始时长的2倍(" + maxTotalExtend + "分钟)");
        }

        // Update deadlineAt and extend counters
        task.setDeadlineAt(task.getDeadlineAt().plusMinutes(extendMinutes));
        task.setExtendCount(task.getExtendCount() + 1);
        task.setExtendTotalMinutes(task.getExtendTotalMinutes() + extendMinutes);
        taskRepository.save(task);

        // Notify winner
        if (task.getWinnerId() != null) {
            notificationService.createNotification(
                    task.getWinnerId(),
                    NotificationType.TASK_UPDATE,
                    "任务延期",
                    "发布者已将任务「" + task.getTitle() + "」的截止时间延长了" + extendMinutes + "分钟",
                    "/tasks/" + taskId);
        }

        auditLogService.log(publisherId, AuditActionType.TASK_PUBLISH,
                "TASK", taskId, "延期任务: +" + extendMinutes + "分钟 (第" + task.getExtendCount() + "次)", "127.0.0.1");

        log.info("Deadline extended: taskId={}, extendMinutes={}, totalExtend={}",
                taskId, extendMinutes, task.getExtendTotalMinutes());

        User publisher = userRepository.findById(publisherId).orElse(null);
        return convertToTaskDTO(task, publisher);
    }

    // ========================================================================
    // 14. submitAppeal
    // ========================================================================

    @Transactional
    public TaskAppeal submitAppeal(Long taskId, Long userId, TaskAppealRequest request) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(404, "任务不存在"));

        // Task must be IN_PROGRESS or APPEALING (APPEAL)
        if (task.getStatus() != TaskStatus.IN_PROGRESS && task.getStatus() != TaskStatus.APPEALING) {
            throw new BusinessException(400, "当前任务状态不允许提交申诉");
        }

        // User must be publisher or winner
        if (!task.getPublisherId().equals(userId) && !userId.equals(task.getWinnerId())) {
            throw new BusinessException(403, "只有任务发布者或中标者可以提交申诉");
        }

        // Check if there's already a pending appeal
        appealRepository.findByTaskId(taskId).stream()
                .filter(a -> "PENDING".equals(a.getStatus()))
                .findFirst()
                .ifPresent(a -> {
                    throw new BusinessException(400, "该任务已有待处理的申诉");
                });

        // Create TaskAppeal record
        TaskAppeal appeal = new TaskAppeal();
        appeal.setTaskId(taskId);
        appeal.setAppealerId(userId);
        appeal.setReason(request.getReason());
        appeal.setStatus(TaskAppeal.STATUS_PENDING);
        appeal.setCreatedAt(LocalDateTime.now());
        appealRepository.save(appeal);

        // Change task status to APPEALING (APPEAL)
        task.setStatus(TaskStatus.APPEALING);
        task.setAppealAt(LocalDateTime.now());
        taskRepository.save(task);

        // Notify the other party
        Long notifyTargetId = task.getPublisherId().equals(userId) ? task.getWinnerId() : task.getPublisherId();
        if (notifyTargetId != null) {
            notificationService.createNotification(
                    notifyTargetId,
                    NotificationType.TASK_UPDATE,
                    "任务申诉",
                    "任务「" + task.getTitle() + "」已被提交申诉",
                    "/tasks/" + taskId);
        }

        // Record audit log
        auditLogService.log(userId, AuditActionType.APPEAL_SUBMIT,
                "TASK_APPEAL", appeal.getId(), "提交申诉: 任务" + taskId + ", 原因: " + request.getReason(), "127.0.0.1");

        log.info("Appeal submitted: taskId={}, userId={}, appealId={}", taskId, userId, appeal.getId());
        return appeal;
    }

    // ========================================================================
    // 15. getRecommendedTasks
    // ========================================================================

    @Transactional(readOnly = true)
    public List<TaskCardDTO> getRecommendedTasks(Long userId) {
        // Try to find completed tasks where user was the winner
        List<Task> completedTasks = taskRepository.findByWinnerId(userId).stream()
                .filter(t -> t.getStatus() == TaskStatus.COMPLETED && t.getCompletedAt() != null)
                .sorted((a, b) -> b.getCompletedAt().compareTo(a.getCompletedAt()))
                .limit(10)
                .toList();

        if (!completedTasks.isEmpty()) {
            // Find most frequent categories from last 10 completed tasks
            Map<Long, Long> categoryFrequency = new LinkedHashMap<>();
            for (Task task : completedTasks) {
                categoryFrequency.merge(task.getCategoryId(), 1L, Long::sum);
            }

            // Get top categories (sorted by frequency descending)
            List<Long> topCategories = categoryFrequency.entrySet().stream()
                    .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                    .limit(5)
                    .map(Map.Entry::getKey)
                    .toList();

            // Recommend PUBLISHED tasks in those categories
            List<Task> recommended = taskRepository.findRecommendedTasks(
                    topCategories, Pageable.ofSize(20));
            return recommended.stream()
                    .map(this::convertToTaskCardDTO)
                    .toList();
        }

        // No history: recommend hot tasks (most applications in last 7 days, top 20)
        List<Task> hotTasks = taskRepository.findHotTasks(
                LocalDateTime.now().minusDays(7), 20);
        return hotTasks.stream()
                .map(this::convertToTaskCardDTO)
                .toList();
    }

    // ========================================================================
    // Helper: convert Task entity -> TaskDTO
    // ========================================================================

    private TaskDTO convertToTaskDTO(Task task, User publisher) {
        TaskDTO dto = new TaskDTO();
        dto.setId(task.getId());
        dto.setPublisherId(task.getPublisherId());
        if (publisher != null) {
            dto.setPublisherNickname(publisher.getNickname());
            dto.setPublisherCreditScore(publisher.getCreditScore());
        }
        dto.setWinnerId(task.getWinnerId());
        dto.setCategoryId(task.getCategoryId());
        dto.setCategoryName(task.getCategoryName());
        dto.setTitle(task.getTitle());
        dto.setDescription(task.getDescription());
        if (task.getCampus() != null) {
            dto.setCampus(task.getCampus().name());
        }
        dto.setRewardPoints(task.getRewardPoints());
        dto.setDeadlineMinutes(task.getDeadlineMinutes());
        dto.setStatus(task.getStatus().name());
        dto.setPublishedAt(task.getPublishedAt());
        dto.setAwardedAt(task.getAwardedAt());
        dto.setDeadlineAt(task.getDeadlineAt());
        dto.setCompletedAt(task.getCompletedAt());
        dto.setCancelledAt(task.getCancelledAt());
        dto.setExtendCount(task.getExtendCount());
        return dto;
    }

    // ========================================================================
    // Helper: convert Task entity -> TaskCardDTO
    // ========================================================================

    private TaskCardDTO convertToTaskCardDTO(Task task) {
        TaskCardDTO dto = new TaskCardDTO();
        dto.setId(task.getId());
        dto.setTitle(task.getTitle());
        dto.setCategoryName(task.getCategoryName());
        dto.setRewardPoints(task.getRewardPoints());
        dto.setDeadlineMinutes(task.getDeadlineMinutes());
        dto.setStatus(task.getStatus().name());
        dto.setPublishedAt(task.getPublishedAt());

        // Calculate remaining time string
        if (task.getDeadlineAt() != null) {
            long remainingMinutes = ChronoUnit.MINUTES.between(LocalDateTime.now(), task.getDeadlineAt());
            if (remainingMinutes <= 0) {
                dto.setRemainingTime("已截止");
            } else if (remainingMinutes < 60) {
                dto.setRemainingTime(remainingMinutes + "分钟");
            } else if (remainingMinutes < 1440) {
                dto.setRemainingTime((remainingMinutes / 60) + "小时");
            } else {
                dto.setRemainingTime((remainingMinutes / 1440) + "天");
            }
        } else {
            // For PUBLISHED tasks, show auto-cancel remaining time
            if (TaskStatus.PUBLISHED == task.getStatus() && task.getPublishedAt() != null) {
                long remainingMinutes = ChronoUnit.MINUTES.between(
                        LocalDateTime.now(),
                        task.getPublishedAt().plusDays(task.getAutoCancelDays()));
                if (remainingMinutes <= 0) {
                    dto.setRemainingTime("即将过期");
                } else {
                    dto.setRemainingTime((remainingMinutes / 1440) + "天");
                }
            } else {
                dto.setRemainingTime("--");
            }
        }

        // Get publisher nickname
        userRepository.findById(task.getPublisherId()).ifPresent(pub -> {
            dto.setPublisherNickname(pub.getNickname());
        });

        return dto;
    }

    // ========================================================================
    // Helper: convert TaskApplication -> ApplicationDTO
    // ========================================================================

    private ApplicationDTO convertToApplicationDTO(TaskApplication app) {
        ApplicationDTO dto = new ApplicationDTO();
        dto.setId(app.getId());
        dto.setTaskId(app.getTaskId());
        dto.setApplicantId(app.getApplicantId());
        dto.setApplyReason(app.getApplyReason());
        dto.setStatus(app.getStatus().name());
        dto.setAppliedAt(app.getAppliedAt());

        // Get applicant info
        userRepository.findById(app.getApplicantId()).ifPresent(user -> {
            dto.setApplicantNickname(user.getNickname());
            dto.setApplicantCreditScore(user.getCreditScore());
        });

        return dto;
    }

    // ========================================================================
    // Helper: convert TaskApplication -> MyApplicationDTO
    // ========================================================================

    private MyApplicationDTO convertToMyApplicationDTO(TaskApplication app) {
        MyApplicationDTO dto = new MyApplicationDTO();
        dto.setId(app.getId());
        dto.setTaskId(app.getTaskId());
        dto.setAppliedAt(app.getAppliedAt());
        dto.setStatus(app.getStatus().name());

        // Get task info
        taskRepository.findById(app.getTaskId()).ifPresent(task -> {
            dto.setTaskTitle(task.getTitle());
            dto.setTaskRewardPoints(task.getRewardPoints());
        });

        return dto;
    }

    // ========================================================================
    // Helper: convert MessageBoard -> MessageDTO
    // ========================================================================

    private MessageDTO convertToMessageDTO(MessageBoard msg) {
        MessageDTO dto = new MessageDTO();
        dto.setId(msg.getId());
        dto.setSenderId(msg.getSenderId());
        dto.setSenderName(msg.getSenderName());
        dto.setContent(msg.getContent());
        dto.setSentAt(msg.getSentAt());
        return dto;
    }

    // ========================================================================
    // Helper: Sensitive word filter
    // ========================================================================

    /**
     * Check if content contains banned words.
     * Returns violation count for this check.
     */
    private int checkSensitiveWords(String content, Long userId) {
        int violations = 0;
        for (String bannedWord : BANNED_WORDS) {
            if (content.contains(bannedWord)) {
                violations++;
            }
        }

        if (violations > 0) {
            LocalDateTime now = LocalDateTime.now();
            List<LocalDateTime> userViolations = violationTracker.computeIfAbsent(userId, k -> new ArrayList<>());

            // Clean up old violations (older than 1 day)
            userViolations.removeIf(time -> time.isBefore(now.minusDays(1)));

            // Add current violations
            for (int i = 0; i < violations; i++) {
                userViolations.add(now);
            }

            // Check if user exceeded daily violation limit
            if (userViolations.size() > 10) {
                log.warn("User {} exceeded daily sensitive word violation limit ({} violations)",
                        userId, userViolations.size());
            }

            log.info("Sensitive word violation: userId={}, violations={}, totalToday={}",
                    userId, violations, userViolations.size());
        }

        return violations;
    }

    // ========================================================================
    // Helper: Get current user ID or null if not authenticated
    // ========================================================================

    private Long getCurrentUserIdOrNull() {
        try {
            return userContext.getCurrentUserId();
        } catch (Exception e) {
            return null;
        }
    }
}
