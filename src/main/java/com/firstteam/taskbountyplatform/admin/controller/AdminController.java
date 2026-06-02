package com.firstteam.taskbountyplatform.admin.controller;

import com.firstteam.taskbountyplatform.admin.dto.*;
import com.firstteam.taskbountyplatform.admin.service.AdminService;
import com.firstteam.taskbountyplatform.audit.entity.AuditLog;
import com.firstteam.taskbountyplatform.auth.security.UserContext;
import com.firstteam.taskbountyplatform.common.response.ApiResponse;
import com.firstteam.taskbountyplatform.common.response.PageResult;
import com.firstteam.taskbountyplatform.task.entity.Task;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasAuthority('ADMIN')")
public class AdminController {

    private final AdminService adminService;
    private final UserContext userContext;

    public AdminController(AdminService adminService,
                           UserContext userContext) {
        this.adminService = adminService;
        this.userContext = userContext;
    }

    private Long getAdminId() {
        return userContext.getCurrentUserId();
    }

    // ==================== Dashboard ====================

    @GetMapping("/dashboard")
    public ApiResponse<AdminDashboardDTO> getDashboard() {
        return ApiResponse.success(adminService.getDashboard());
    }

    // ==================== User Management ====================

    @GetMapping("/users")
    public ApiResponse<PageResult<UserManagementDTO>> listUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer minScore,
            @RequestParam(required = false) Integer maxScore,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ApiResponse.success(adminService.listUsers(keyword, status, minScore, maxScore, pageable));
    }

    @GetMapping("/users/{userId}")
    public ApiResponse<UserManagementDTO> getUserDetail(@PathVariable Long userId) {
        return ApiResponse.success(adminService.getUserDetail(userId));
    }

    @PostMapping("/users/{userId}/freeze")
    public ApiResponse<Map<String, String>> freezeUser(
            @PathVariable Long userId,
            @Valid @RequestBody FreezeUserRequest request) {
        adminService.freezeUser(userId, request, getAdminId());
        Map<String, String> result = new HashMap<>();
        result.put("message", "User frozen successfully");
        return ApiResponse.success(result);
    }

    @PostMapping("/users/{userId}/unfreeze")
    public ApiResponse<Map<String, String>> unfreezeUser(
            @PathVariable Long userId,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        adminService.unfreezeUser(userId, reason, getAdminId());
        Map<String, String> result = new HashMap<>();
        result.put("message", "User unfrozen successfully");
        return ApiResponse.success(result);
    }

    @PostMapping("/users/{userId}/credit-reset")
    public ApiResponse<Map<String, String>> resetUserCredit(
            @PathVariable Long userId,
            @Valid @RequestBody AdminCreditResetRequest request) {
        adminService.resetUserCredit(userId, request, getAdminId());
        Map<String, String> result = new HashMap<>();
        result.put("message", "Credit reset successfully");
        return ApiResponse.success(result);
    }

    @GetMapping("/users/{userId}/audit-logs")
    public ApiResponse<PageResult<AuditLog>> getUserAuditLogs(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ApiResponse.success(adminService.getUserAuditLogs(userId, pageable));
    }

    @PostMapping("/users/graduated/handle")
    public ApiResponse<List<UserManagementDTO>> handleGraduatedUsers() {
        return ApiResponse.success(adminService.handleGraduatedUsers(getAdminId()));
    }

    @PostMapping("/users/{userId}/graduated/defer")
    public ApiResponse<Map<String, String>> deferGraduationFreeze(
            @PathVariable Long userId,
            @RequestBody Map<String, Integer> body) {
        int days = body.getOrDefault("days", 30);
        adminService.deferGraduationFreeze(userId, days, getAdminId());
        Map<String, String> result = new HashMap<>();
        result.put("message", "Graduation freeze deferred for " + days + " days");
        return ApiResponse.success(result);
    }

    // ==================== Task Management ====================

    @GetMapping("/tasks")
    public ApiResponse<PageResult<Task>> listAllTasks(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ApiResponse.success(adminService.listAllTasks(status, keyword, pageable));
    }

    @PostMapping("/tasks/{taskId}/force-cancel")
    public ApiResponse<Map<String, String>> forceCancelTask(
            @PathVariable Long taskId,
            @Valid @RequestBody ForceCancelTaskRequest request) {
        adminService.forceCancelTask(taskId, request.getReason(), getAdminId());
        Map<String, String> result = new HashMap<>();
        result.put("message", "Task force-cancelled successfully");
        return ApiResponse.success(result);
    }

    @PostMapping("/tasks/migrate-category")
    public ApiResponse<Map<String, String>> migrateTaskCategory(
            @Valid @RequestBody MigrateCategoryRequest request) {
        adminService.migrateTaskCategory(request.getCategoryId(), request.getTargetCategoryId(),
                request.getTaskIds(), getAdminId());
        Map<String, String> result = new HashMap<>();
        result.put("message", "Tasks migrated successfully");
        return ApiResponse.success(result);
    }

    // ==================== Review Audits (头像/昵称/公告栏审核) ====================

    @GetMapping("/review-audits")
    public ApiResponse<PageResult<ReviewAuditDTO>> listReviewAudits(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "submittedAt"));
        return ApiResponse.success(adminService.listReviewAudits(type, status, pageable));
    }

    @PostMapping("/review-audits/{auditId}/approve")
    public ApiResponse<Map<String, String>> approveReviewAudit(@PathVariable Long auditId) {
        adminService.approveReviewAudit(auditId, getAdminId());
        Map<String, String> result = new HashMap<>();
        result.put("message", "Review audit approved successfully");
        return ApiResponse.success(result);
    }

    @PostMapping("/review-audits/{auditId}/reject")
    public ApiResponse<Map<String, String>> rejectReviewAudit(
            @PathVariable Long auditId,
            @RequestBody Map<String, String> body) {
        String reason = body.getOrDefault("reason", "No reason provided");
        adminService.rejectReviewAudit(auditId, reason, getAdminId());
        Map<String, String> result = new HashMap<>();
        result.put("message", "Review audit rejected successfully");
        return ApiResponse.success(result);
    }

    // ==================== System Config ====================

    @GetMapping("/configs")
    public ApiResponse<List<SystemConfigDTO>> getAllConfigs() {
        return ApiResponse.success(adminService.getAllConfigs());
    }

    @PutMapping("/configs/{configId}")
    public ApiResponse<SystemConfigDTO> updateConfig(
            @PathVariable Long configId,
            @Valid @RequestBody SystemConfigUpdateRequest request) {
        return ApiResponse.success(adminService.updateConfig(configId, request.getConfigValue(), getAdminId()));
    }

    @GetMapping("/configs/{key}")
    public ApiResponse<SystemConfigDTO> getConfig(@PathVariable String key) {
        return ApiResponse.success(adminService.getConfig(key));
    }

    // ==================== Broadcast ====================

    @PostMapping("/broadcasts")
    public ApiResponse<Map<String, String>> sendBroadcast(
            @Valid @RequestBody BroadcastRequest request) {
        adminService.sendBroadcast(request, getAdminId());
        Map<String, String> result = new HashMap<>();
        result.put("message", "Broadcast sent successfully");
        return ApiResponse.success(result);
    }

    @PostMapping("/broadcasts/scheduled")
    public ApiResponse<Map<String, String>> createScheduledBroadcast(
            @Valid @RequestBody BroadcastRequest request,
            @RequestParam String cronExpression) {
        adminService.createScheduledBroadcast(request, cronExpression, getAdminId());
        Map<String, String> result = new HashMap<>();
        result.put("message", "Scheduled broadcast created successfully");
        return ApiResponse.success(result);
    }
}
