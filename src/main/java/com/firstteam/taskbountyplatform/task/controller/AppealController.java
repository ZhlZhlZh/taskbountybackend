package com.firstteam.taskbountyplatform.task.controller;

import com.firstteam.taskbountyplatform.auth.security.UserContext;
import com.firstteam.taskbountyplatform.common.response.ApiResponse;
import com.firstteam.taskbountyplatform.common.response.PageResult;
import com.firstteam.taskbountyplatform.task.dto.AppealProcessRequest;
import com.firstteam.taskbountyplatform.task.entity.TaskAppeal;
import com.firstteam.taskbountyplatform.task.service.AppealService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class AppealController {

    private final AppealService appealService;
    private final UserContext userContext;

    public AppealController(AppealService appealService, UserContext userContext) {
        this.appealService = appealService;
        this.userContext = userContext;
    }

    /**
     * GET /api/admin/appeals - Get pending appeals (admin only).
     */
    @GetMapping("/admin/appeals")
    public ApiResponse<PageResult<TaskAppeal>> getPendingAppeals(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int size) {
        if (!userContext.isAdmin()) {
            return ApiResponse.error(403, "无权限访问");
        }
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by("createdAt").descending());
        Page<TaskAppeal> appealPage = appealService.getPendingAppeals(pageable);
        PageResult<TaskAppeal> result = new PageResult<>(
                appealPage.getContent(), page, size, appealPage.getTotalElements());
        return ApiResponse.success(result);
    }

    /**
     * GET /api/admin/appeals/{appealId} - Get appeal detail (admin only).
     */
    @GetMapping("/admin/appeals/{appealId}")
    public ApiResponse<TaskAppeal> getAppealDetail(@PathVariable Long appealId) {
        if (!userContext.isAdmin()) {
            return ApiResponse.error(403, "无权限访问");
        }
        TaskAppeal appeal = appealService.getAppealDetail(appealId);
        return ApiResponse.success(appeal);
    }

    /**
     * POST /api/admin/appeals/{appealId}/process - Process appeal (admin only).
     */
    @PostMapping("/admin/appeals/{appealId}/process")
    public ApiResponse<String> processAppeal(@PathVariable Long appealId,
                                             @Valid @RequestBody AppealProcessRequest request) {
        if (!userContext.isAdmin()) {
            return ApiResponse.error(403, "无权限访问");
        }
        Long adminId = userContext.getCurrentUserId();
        appealService.processAppeal(appealId, adminId, request);
        return ApiResponse.success("申诉处理完成，裁定: " + request.getDecision());
    }

    /**
     * GET /api/tasks/{taskId}/appeals - Get appeals for a task (publisher/worker/admin).
     */
    @GetMapping("/tasks/{taskId}/appeals")
    public ApiResponse<List<TaskAppeal>> getAppealsForTask(@PathVariable Long taskId) {
        List<TaskAppeal> appeals = appealService.getAppealsForTask(taskId);
        return ApiResponse.success(appeals);
    }
}
