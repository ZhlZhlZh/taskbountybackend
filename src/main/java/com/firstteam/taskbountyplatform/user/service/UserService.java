package com.firstteam.taskbountyplatform.user.service;

import com.firstteam.taskbountyplatform.admin.entity.ReviewAudit;
import com.firstteam.taskbountyplatform.admin.repository.ReviewAuditRepository;
import com.firstteam.taskbountyplatform.common.enums.AuditActionType;
import com.firstteam.taskbountyplatform.common.enums.AuditItemType;
import com.firstteam.taskbountyplatform.audit.entity.AuditLog;
import com.firstteam.taskbountyplatform.audit.repository.AuditLogRepository;
import com.firstteam.taskbountyplatform.auth.security.UserContext;
import com.firstteam.taskbountyplatform.common.enums.ReviewAuditStatus;
import com.firstteam.taskbountyplatform.common.enums.TaskStatus;
import com.firstteam.taskbountyplatform.config.FileUploadConfig;
import com.firstteam.taskbountyplatform.config.PlatformConfig;
import com.firstteam.taskbountyplatform.credit.entity.CreditRecord;
import com.firstteam.taskbountyplatform.credit.repository.CreditRecordRepository;
import com.firstteam.taskbountyplatform.file.entity.FileObject;
import com.firstteam.taskbountyplatform.file.repository.FileObjectRepository;
import com.firstteam.taskbountyplatform.point.entity.PointAccount;
import com.firstteam.taskbountyplatform.point.repository.PointAccountRepository;
import com.firstteam.taskbountyplatform.notification.service.NotificationService;
import com.firstteam.taskbountyplatform.review.repository.ReviewRepository;
import com.firstteam.taskbountyplatform.task.repository.TaskApplicationRepository;
import com.firstteam.taskbountyplatform.task.repository.TaskRepository;
import com.firstteam.taskbountyplatform.user.dto.*;
import com.firstteam.taskbountyplatform.user.entity.User;
import com.firstteam.taskbountyplatform.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PointAccountRepository pointAccountRepository;
    private final ReviewAuditRepository reviewAuditRepository;
    private final TaskApplicationRepository taskApplicationRepository;
    private final TaskRepository taskRepository;
    private final ReviewRepository reviewRepository;
    private final CreditRecordRepository creditRecordRepository;
    private final FileObjectRepository fileObjectRepository;
    private final NotificationService notificationService;
    private final AuditLogRepository auditLogRepository;
    private final UserContext userContext;
    private final PlatformConfig platformConfig;
    private final FileUploadConfig fileUploadConfig;

    /** Pattern: only Chinese characters, English letters, Arabic digits. No spaces, no special chars. */
    private static final Pattern NICKNAME_PATTERN = Pattern.compile("^[\\u4e00-\\u9fa5a-zA-Z0-9]+$");

    private static final String AVATAR_UPLOAD_DIR = "./uploads/avatars";

    public UserService(UserRepository userRepository,
                       PointAccountRepository pointAccountRepository,
                       ReviewAuditRepository reviewAuditRepository,
                       TaskApplicationRepository taskApplicationRepository,
                       TaskRepository taskRepository,
                       ReviewRepository reviewRepository,
                       CreditRecordRepository creditRecordRepository,
                       FileObjectRepository fileObjectRepository,
                       NotificationService notificationService,
                       AuditLogRepository auditLogRepository,
                       UserContext userContext,
                       PlatformConfig platformConfig,
                       FileUploadConfig fileUploadConfig) {
        this.userRepository = userRepository;
        this.pointAccountRepository = pointAccountRepository;
        this.reviewAuditRepository = reviewAuditRepository;
        this.taskApplicationRepository = taskApplicationRepository;
        this.taskRepository = taskRepository;
        this.reviewRepository = reviewRepository;
        this.creditRecordRepository = creditRecordRepository;
        this.fileObjectRepository = fileObjectRepository;
        this.notificationService = notificationService;
        this.auditLogRepository = auditLogRepository;
        this.userContext = userContext;
        this.platformConfig = platformConfig;
        this.fileUploadConfig = fileUploadConfig;
    }

    /**
     * Get full profile for current user or admin, otherwise return public profile.
     */
    public Object getProfile(Long userId) {
        Long currentUserId = userContext.getCurrentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        if (userId.equals(currentUserId) || userContext.isAdmin()) {
            return buildUserProfileDTO(user);
        }
        return buildPublicUserDTO(user);
    }

    /**
     * Get public profile for any user.
     */
    public PublicUserDTO getPublicProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        return buildPublicUserDTO(user);
    }

    /**
     * Request a nickname change (requires admin audit).
     */
    @Transactional
    public void requestNicknameChange(String newNickname) {
        Long currentUserId = userContext.getCurrentUserId();
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        // Validate format: only Chinese chars, English letters, Arabic digits
        if (!NICKNAME_PATTERN.matcher(newNickname).matches()) {
            throw new RuntimeException("昵称只能包含中文、英文和阿拉伯数字，不能包含空格和特殊字符");
        }

        // Validate length < 10 chars (each char counts as 1)
        int charCount = newNickname.codePointCount(0, newNickname.length());
        if (charCount >= 10) {
            throw new RuntimeException("昵称长度不能超过9个字");
        }

        // Validate not same as current
        if (newNickname.equals(user.getNickname())) {
            throw new RuntimeException("新昵称不能与当前昵称相同");
        }

        // Check 30-day cooldown since last approved nickname change
        checkNicknameCooldown(currentUserId);

        // Check nickname uniqueness (including locked ones in pending audits)
        checkNicknameUniqueness(newNickname);

        // Create ReviewAudit record
        ReviewAudit audit = new ReviewAudit();
        audit.setApplicantId(currentUserId);
        audit.setAuditType(AuditItemType.NICKNAME);
        audit.setOldValue(user.getNickname());
        audit.setNewValue(newNickname);
        audit.setStatus(ReviewAuditStatus.PENDING);
        audit.setSubmittedAt(LocalDateTime.now());
        audit.setTimeoutAt(LocalDateTime.now().plusHours(24));
        reviewAuditRepository.save(audit);

        // Record audit log
        AuditLog auditLog = new AuditLog();
        auditLog.setOperatorId(currentUserId);
        auditLog.setActionType(AuditActionType.NICKNAME_CHANGE);
        auditLog.setTargetType("USER");
        auditLog.setTargetId(currentUserId);
        auditLog.setDetail("申请修改昵称，原昵称：" + user.getNickname() + "，新昵称：" + newNickname + "，审核ID：" + audit.getId());
        auditLog.setIp("system");
        auditLogRepository.save(auditLog);
    }

    /**
     * Request an avatar change (requires admin audit).
     */
    @Transactional
    public void requestAvatarChange(MultipartFile file) {
        Long currentUserId = userContext.getCurrentUserId();
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        // Validate size < 1MB
        if (file.getSize() > fileUploadConfig.getAvatarMaxSize()) {
            throw new RuntimeException("头像文件不能超过 1MB");
        }

        // Validate format: jpg/png only
        String contentType = file.getContentType();
        if (contentType == null || (!contentType.equals("image/jpeg") && !contentType.equals("image/png"))) {
            throw new RuntimeException("头像仅支持 JPG/PNG 格式");
        }

        // Validate dimensions: exactly 225x225
        try {
            BufferedImage image = ImageIO.read(file.getInputStream());
            if (image == null) {
                throw new RuntimeException("无法读取图片文件");
            }
            int width = image.getWidth();
            int height = image.getHeight();
            if (width != fileUploadConfig.getAvatarWidth() || height != fileUploadConfig.getAvatarHeight()) {
                throw new RuntimeException("头像尺寸必须为 " + fileUploadConfig.getAvatarWidth()
                        + "x" + fileUploadConfig.getAvatarHeight() + "，当前尺寸：" + width + "x" + height);
            }
        } catch (IOException e) {
            throw new RuntimeException("读取图片信息失败：" + e.getMessage());
        }

        // Check no existing pending avatar audit
        List<ReviewAudit> pendingAudits = reviewAuditRepository
                .findByApplicantIdAndAuditTypeAndStatus(currentUserId, "AVATAR", ReviewAuditStatus.PENDING);
        if (!pendingAudits.isEmpty()) {
            throw new RuntimeException("您已有待审核的头像申请，请等待审核完成后再提交新申请");
        }

        // Save file temporarily
        String filePath;
        try {
            filePath = saveTempAvatar(file, currentUserId);
        } catch (IOException e) {
            throw new RuntimeException("头像文件保存失败：" + e.getMessage());
        }

        // Create ReviewAudit record
        ReviewAudit audit = new ReviewAudit();
        audit.setApplicantId(currentUserId);
        audit.setAuditType(AuditItemType.AVATAR);
        audit.setOldValue(user.getAvatarUrl());
        audit.setNewValue(filePath);
        audit.setStatus(ReviewAuditStatus.PENDING);
        audit.setSubmittedAt(LocalDateTime.now());
        audit.setTimeoutAt(LocalDateTime.now().plusHours(24));
        reviewAuditRepository.save(audit);

        // Record audit log
        AuditLog auditLog = new AuditLog();
        auditLog.setOperatorId(currentUserId);
        auditLog.setActionType(AuditActionType.AVATAR_CHANGE);
        auditLog.setTargetType("USER");
        auditLog.setTargetId(currentUserId);
        auditLog.setDetail("申请修改头像，原头像：" + user.getAvatarUrl() + "，新头像文件：" + filePath + "，审核ID：" + audit.getId());
        auditLog.setIp("system");
        auditLogRepository.save(auditLog);
    }

    /**
     * Request an announcement change (requires admin audit).
     */
    @Transactional
    public void requestAnnouncementChange(String announcement) {
        Long currentUserId = userContext.getCurrentUserId();
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        // Validate length <= 200
        if (announcement != null && announcement.length() > 200) {
            throw new RuntimeException("公告栏内容不能超过200个字符");
        }

        // Create ReviewAudit record
        ReviewAudit audit = new ReviewAudit();
        audit.setApplicantId(currentUserId);
        audit.setAuditType(AuditItemType.ANNOUNCEMENT);
        audit.setOldValue(user.getAnnouncement() != null ? user.getAnnouncement() : "");
        audit.setNewValue(announcement != null ? announcement : "");
        audit.setStatus(ReviewAuditStatus.PENDING);
        audit.setSubmittedAt(LocalDateTime.now());
        audit.setTimeoutAt(LocalDateTime.now().plusHours(24));
        reviewAuditRepository.save(audit);

        // Record audit log
        AuditLog auditLog = new AuditLog();
        auditLog.setOperatorId(currentUserId);
        auditLog.setActionType(AuditActionType.ANNOUNCEMENT_CHANGE);
        auditLog.setTargetType("USER");
        auditLog.setTargetId(currentUserId);
        auditLog.setDetail("申请修改公告栏，原内容：" + user.getAnnouncement() + "，新内容：" + announcement + "，审核ID：" + audit.getId());
        auditLog.setIp("system");
        auditLogRepository.save(auditLog);
    }

    /**
     * Get paginated credit records for the current user.
     */
    public Page<CreditRecordDTO> getCreditRecords(Pageable pageable) {
        Long currentUserId = userContext.getCurrentUserId();
        Page<CreditRecord> records = creditRecordRepository.findByUserIdOrderByCreatedAtDesc(currentUserId, pageable);

        return records.map(record -> {
            CreditRecordDTO dto = new CreditRecordDTO();
            dto.setId(record.getId());
            dto.setChangeScore(record.getChangeScore());
            dto.setReasonType(record.getReasonType() != null ? record.getReasonType().name() : null);
            dto.setBeforeScore(record.getBeforeScore());
            dto.setAfterScore(record.getAfterScore());
            dto.setTaskId(record.getTaskId());
            dto.setDescription(record.getDescription());
            dto.setCreatedAt(record.getCreatedAt());
            return dto;
        });
    }

    /**
     * Get user statistics: completion rate, published task count, completed task count.
     */
    public UserStatisticsDTO getStatistics(Long userId) {
        UserStatisticsDTO dto = new UserStatisticsDTO();

        // Published task count
        List<com.firstteam.taskbountyplatform.task.entity.Task> publishedTasks =
                taskRepository.findByPublisherId(userId);
        int publishedCount = publishedTasks.size();
        dto.setPublishedTaskCount(publishedCount);

        // Completed task count (tasks where this user is the winner and status = COMPLETED)
        List<com.firstteam.taskbountyplatform.task.entity.Task> winnerTasks =
                taskRepository.findByWinnerId(userId);
        int completedCount = (int) winnerTasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.COMPLETED)
                .count();
        dto.setCompletedTaskCount(completedCount);

        // Cancelled task count
        int cancelledCount = (int) winnerTasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.CANCELLED)
                .count();
        dto.setCancelledTaskCount(cancelledCount);

        // Completion rate
        dto.setCompletionRate(calculateCompletionRate(userId));

        return dto;
    }

    /**
     * Calculate the completion rate for a user.
     * completionRate = completedCount / totalAcceptedCount * 100 (rounded up).
     * Returns "N/A" if totalAcceptedCount = 0.
     */
    public String calculateCompletionRate(Long userId) {
        // Total accepted count = count of AWARDED applications for this user
        long totalAcceptedCount = taskApplicationRepository.countActiveOrders(userId);

        // Also count completed ones - tasks where user is winner and status = COMPLETED
        List<com.firstteam.taskbountyplatform.task.entity.Task> winnerTasks =
                taskRepository.findByWinnerId(userId);
        long completedCount = winnerTasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.COMPLETED)
                .count();

        // Use a broader measure: count all awarded (active) + completed
        long totalAwarded = totalAcceptedCount + completedCount;

        if (totalAwarded == 0) {
            return "N/A";
        }

        double rate = (double) completedCount / totalAwarded * 100;
        int roundedRate = (int) Math.ceil(rate);
        return roundedRate + "%";
    }

    // ========== Private Helper Methods ==========

    /**
     * Build full UserProfileDTO from user entity and related data.
     */
    private UserProfileDTO buildUserProfileDTO(User user) {
        UserProfileDTO dto = new UserProfileDTO();
        dto.setId(user.getId());
        dto.setStudentNo(user.getStudentNo());
        dto.setRealName(user.getRealName());
        dto.setNickname(user.getNickname());
        dto.setAvatarUrl(user.getAvatarUrl());
        dto.setAnnouncement(user.getAnnouncement());
        dto.setGrade(user.getGrade());
        dto.setCollege(user.getCollege());
        dto.setAcademy(user.getAcademy());
        dto.setCreditScore(user.getCreditScore());
        dto.setAccountStatus(user.getAccountStatus() != null ? user.getAccountStatus().name() : null);
        dto.setRole(user.getRole() != null ? user.getRole().name() : null);
        dto.setCreatedAt(user.getCreatedAt());

        // Point account info
        Optional<PointAccount> accountOpt = pointAccountRepository.findByUserId(user.getId());
        if (accountOpt.isPresent()) {
            PointAccount account = accountOpt.get();
            dto.setAvailablePoints(account.getAvailablePoints());
            dto.setFrozenPoints(account.getFrozenPoints());
            dto.setTotalIncome(account.getTotalIncome());
            dto.setTotalExpense(account.getTotalExpense());
        }

        // Completion rate
        dto.setCompletionRate(calculateCompletionRate(user.getId()));

        return dto;
    }

    /**
     * Build PublicUserDTO from user entity.
     */
    private PublicUserDTO buildPublicUserDTO(User user) {
        PublicUserDTO dto = new PublicUserDTO();
        dto.setId(user.getId());
        dto.setNickname(user.getNickname());
        dto.setAvatarUrl(user.getAvatarUrl());
        dto.setAnnouncement(user.getAnnouncement());
        dto.setCreditScore(user.getCreditScore());
        dto.setCompletionRate(calculateCompletionRate(user.getId()));
        dto.setCreatedAt(user.getCreatedAt());
        return dto;
    }

    /**
     * Check 30-day cooldown on nickname change.
     * Uses ReviewAudit records to find the last approved nickname change.
     */
    private void checkNicknameCooldown(Long userId) {
        // Find the most recently approved nickname audit for this user
        List<ReviewAudit> audits = reviewAuditRepository
                .findByApplicantIdAndAuditTypeAndStatus(userId, "NICKNAME", ReviewAuditStatus.APPROVED);
        if (!audits.isEmpty()) {
            // Sort by processedAt descending
            LocalDateTime lastApprovedAt = audits.stream()
                    .map(ReviewAudit::getProcessedAt)
                    .filter(p -> p != null)
                    .max(LocalDateTime::compareTo)
                    .orElse(null);

            if (lastApprovedAt != null) {
                int cooldownDays = platformConfig.getNicknameModifyCooldownDays();
                LocalDateTime cooldownEnd = lastApprovedAt.plusDays(cooldownDays);
                if (LocalDateTime.now().isBefore(cooldownEnd)) {
                    long remainingDays = java.time.Duration.between(LocalDateTime.now(), cooldownEnd).toDays();
                    throw new RuntimeException("昵称修改冷却期为 " + cooldownDays + " 天，还需等待 "
                            + Math.max(1, remainingDays) + " 天");
                }
            }
        }
    }

    /**
     * Check nickname uniqueness including pending audits.
     */
    private void checkNicknameUniqueness(String newNickname) {
        // Check existing users
        if (userRepository.existsByNickname(newNickname)) {
            throw new RuntimeException("该昵称已被其他用户使用");
        }

        Long currentUserId = userContext.getCurrentUserId();

        // Check pending nickname audits from other users
        List<ReviewAudit> pendingNicknameAudits = reviewAuditRepository
                .findByApplicantIdAndAuditTypeAndStatus(currentUserId, "NICKNAME", ReviewAuditStatus.PENDING);
        // Check across all users for pending nickname changes that match the new nickname
        // This checks if another user has a pending audit with the same target nickname
        for (ReviewAudit audit : pendingNicknameAudits) {
            // Only check our own pending audits to see if we already have one
            if (audit.getAuditType() == AuditItemType.NICKNAME
                    && audit.getStatus() == ReviewAuditStatus.PENDING) {
                throw new RuntimeException("您已有待审核的昵称申请，请等待审核完成后再提交新申请");
            }
        }

        // Also check all pending nickname audits across all users
        // (page through pending audits to find conflicts)
        Page<ReviewAudit> allPendingNicknames = reviewAuditRepository
                .findByAuditType("NICKNAME", PageRequest.of(0, 1000));
        boolean conflict = allPendingNicknames.getContent().stream()
                .anyMatch(a -> newNickname.equals(a.getNewValue())
                        && a.getStatus() == ReviewAuditStatus.PENDING
                        && !a.getApplicantId().equals(currentUserId));
        if (conflict) {
            throw new RuntimeException("该昵称已被其他用户申请审核中，请选择其他昵称");
        }
    }

    /**
     * Save a temporary avatar file to disk.
     */
    private String saveTempAvatar(MultipartFile file, Long userId) throws IOException {
        String originalName = file.getOriginalFilename();
        String extension = "";
        if (originalName != null && originalName.contains(".")) {
            extension = originalName.substring(originalName.lastIndexOf("."));
        }
        String storedName = "temp_avatar_" + userId + "_" + UUID.randomUUID().toString() + extension;

        Path uploadPath = Paths.get(AVATAR_UPLOAD_DIR);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        Path filePath = uploadPath.resolve(storedName);
        file.transferTo(filePath.toFile());

        return "/uploads/avatars/" + storedName;
    }
}
