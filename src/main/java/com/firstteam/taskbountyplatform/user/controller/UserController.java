package com.firstteam.taskbountyplatform.user.controller;

import com.firstteam.taskbountyplatform.auth.security.UserContext;
import com.firstteam.taskbountyplatform.common.response.ApiResponse;
import com.firstteam.taskbountyplatform.common.response.PageResult;
import com.firstteam.taskbountyplatform.point.dto.PointFlowDTO;
import com.firstteam.taskbountyplatform.point.entity.PointFlow;
import com.firstteam.taskbountyplatform.point.repository.PointFlowRepository;
import com.firstteam.taskbountyplatform.user.dto.*;
import com.firstteam.taskbountyplatform.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * UserController - REST API for user profile management.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final PointFlowRepository pointFlowRepository;
    private final UserContext userContext;

    public UserController(UserService userService,
                           PointFlowRepository pointFlowRepository,
                           UserContext userContext) {
        this.userService = userService;
        this.pointFlowRepository = pointFlowRepository;
        this.userContext = userContext;
    }

    /**
     * GET /api/users/me - get current user full profile.
     */
    @GetMapping("/me")
    public ApiResponse<UserProfileDTO> getCurrentUserProfile() {
        Long currentUserId = userContext.getCurrentUserId();
        Object profile = userService.getProfile(currentUserId);
        if (profile instanceof UserProfileDTO) {
            return ApiResponse.success((UserProfileDTO) profile);
        }
        return ApiResponse.success(null);
    }

    /**
     * GET /api/users/{userId} - get user public profile.
     */
    @GetMapping("/{userId}")
    public ApiResponse<PublicUserDTO> getPublicUserProfile(@PathVariable Long userId) {
        PublicUserDTO profile = userService.getPublicProfile(userId);
        return ApiResponse.success(profile);
    }

    /**
     * PUT /api/users/me/nickname - request nickname change.
     * Body: { "newNickname": "..." }
     */
    @PutMapping("/me/nickname")
    public ApiResponse<Void> updateNickname(@Valid @RequestBody UpdateNicknameRequest request) {
        userService.requestNicknameChange(request.getNewNickname());
        return ApiResponse.success("昵称修改申请已提交，请等待管理员审核", null);
    }

    /**
     * PUT /api/users/me/avatar - request avatar change.
     * Accepts multipart file upload.
     */
    @PutMapping("/me/avatar")
    public ApiResponse<Void> updateAvatar(@RequestParam("file") MultipartFile file) {
        userService.requestAvatarChange(file);
        return ApiResponse.success("头像修改申请已提交，请等待管理员审核", null);
    }

    /**
     * PUT /api/users/me/announcement - request announcement change.
     * Body: { "announcement": "..." }
     */
    @PutMapping("/me/announcement")
    public ApiResponse<Void> updateAnnouncement(@Valid @RequestBody UpdateAnnouncementRequest request) {
        userService.requestAnnouncementChange(request.getAnnouncement());
        return ApiResponse.success("公告栏修改申请已提交，请等待管理员审核", null);
    }

    /**
     * GET /api/users/me/credit-records - get own credit score change records.
     */
    @GetMapping("/me/credit-records")
    public ApiResponse<PageResult<CreditRecordDTO>> getCreditRecords(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int size) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<CreditRecordDTO> records = userService.getCreditRecords(pageable);

        PageResult<CreditRecordDTO> result = new PageResult<>(
                records.getContent(), page, size, records.getTotalElements());
        return ApiResponse.success(result);
    }

    /**
     * GET /api/users/me/statistics - get own task statistics.
     */
    @GetMapping("/me/statistics")
    public ApiResponse<UserStatisticsDTO> getStatistics() {
        Long currentUserId = userContext.getCurrentUserId();
        UserStatisticsDTO statistics = userService.getStatistics(currentUserId);
        return ApiResponse.success(statistics);
    }

    /**
     * GET /api/users/me/point-flows - get own point flow history.
     */
    @GetMapping("/me/point-flows")
    public ApiResponse<PageResult<PointFlowDTO>> getPointFlows(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int size) {
        Long currentUserId = userContext.getCurrentUserId();
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<PointFlow> flows = pointFlowRepository.findByUserIdOrderByCreatedAtDesc(currentUserId, pageable);

        List<PointFlowDTO> dtos = flows.getContent().stream()
                .map(this::toPointFlowDTO)
                .toList();

        PageResult<PointFlowDTO> result = new PageResult<>(dtos, page, size, flows.getTotalElements());
        return ApiResponse.success(result);
    }

    // ========== DTO Mapping Helpers ==========

    private PointFlowDTO toPointFlowDTO(PointFlow flow) {
        PointFlowDTO dto = new PointFlowDTO();
        dto.setId(flow.getId());
        dto.setTaskId(flow.getTaskId());
        dto.setChangeAmount(flow.getChangeAmount());
        dto.setBalanceBefore(flow.getBalanceBefore());
        dto.setBalanceAfter(flow.getBalanceAfter());
        dto.setFlowType(flow.getFlowType() != null ? flow.getFlowType().name() : null);
        dto.setDescription(flow.getDescription());
        dto.setCreatedAt(flow.getCreatedAt());
        return dto;
    }
}
