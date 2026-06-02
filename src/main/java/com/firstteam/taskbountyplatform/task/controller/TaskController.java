package com.firstteam.taskbountyplatform.task.controller;

import com.firstteam.taskbountyplatform.auth.security.UserContext;
import com.firstteam.taskbountyplatform.common.response.ApiResponse;
import com.firstteam.taskbountyplatform.common.response.PageResult;
import com.firstteam.taskbountyplatform.file.entity.FileObject;
import com.firstteam.taskbountyplatform.file.service.FileService;
import com.firstteam.taskbountyplatform.task.dto.*;
import com.firstteam.taskbountyplatform.task.entity.TaskAppeal;
import com.firstteam.taskbountyplatform.task.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * TaskController - REST API for task operations.
 * Base path: /api/tasks
 */
@RestController
@RequestMapping("/api")
public class TaskController {

    private final TaskService taskService;
    private final FileService fileService;
    private final UserContext userContext;

    public TaskController(TaskService taskService, FileService fileService, UserContext userContext) {
        this.taskService = taskService;
        this.fileService = fileService;
        this.userContext = userContext;
    }

    // ========================================================================
    // POST /api/tasks - Publish a new task
    // ========================================================================

    @PostMapping(value = "/tasks", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<TaskDTO> publishTask(
            @RequestPart("request") @Valid TaskCreateRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        Long publisherId = userContext.getCurrentUserId();
        TaskDTO taskDTO = taskService.publishTask(publisherId, request);

        // Upload files if provided
        if (files != null && !files.isEmpty()) {
            fileService.uploadFiles(files, FileObject.BIZ_TYPE_TASK_ATTACHMENT, taskDTO.getId());
        }

        return ApiResponse.success("任务发布成功", taskDTO);
    }

    // ========================================================================
    // GET /api/tasks - Browse / search tasks
    // ========================================================================

    @GetMapping("/tasks")
    public ApiResponse<PageResult<TaskCardDTO>> browseTasks(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) List<Long> categoryId,
            @RequestParam(required = false) Integer minReward,
            @RequestParam(required = false) Integer maxReward,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false, defaultValue = "time") String sortBy,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size) {
        TaskSearchRequest request = new TaskSearchRequest();
        request.setKeyword(keyword);
        request.setCategoryIds(categoryId);
        request.setMinReward(minReward);
        request.setMaxReward(maxReward);
        if (startDate != null && !startDate.isBlank()) {
            request.setStartDate(java.time.LocalDate.parse(startDate));
        }
        if (endDate != null && !endDate.isBlank()) {
            request.setEndDate(java.time.LocalDate.parse(endDate));
        }
        request.setSortBy(sortBy);
        request.setPage(page);
        request.setSize(size);

        PageResult<TaskCardDTO> result = taskService.browseTasks(request);
        return ApiResponse.success(result);
    }

    // ========================================================================
    // GET /api/tasks/recommended - Get recommended tasks for current user
    // ========================================================================

    @GetMapping("/tasks/recommended")
    public ApiResponse<List<TaskCardDTO>> getRecommendedTasks() {
        Long userId = userContext.getCurrentUserId();
        List<TaskCardDTO> recommended = taskService.getRecommendedTasks(userId);
        return ApiResponse.success(recommended);
    }

    // ========================================================================
    // GET /api/tasks/{taskId} - Get task detail
    // ========================================================================

    @GetMapping("/tasks/{taskId}")
    public ApiResponse<TaskDTO> getTaskDetail(@PathVariable Long taskId) {
        TaskDTO taskDTO = taskService.getTaskDetail(taskId);
        return ApiResponse.success(taskDTO);
    }

    // ========================================================================
    // PUT /api/tasks/{taskId} - Update task
    // ========================================================================

    @PutMapping("/tasks/{taskId}")
    public ApiResponse<TaskDTO> updateTask(
            @PathVariable Long taskId,
            @RequestBody @Valid TaskUpdateRequest request) {
        Long publisherId = userContext.getCurrentUserId();
        TaskDTO taskDTO = taskService.updateTask(taskId, publisherId, request);
        return ApiResponse.success("任务更新成功", taskDTO);
    }

    // ========================================================================
    // DELETE /api/tasks/{taskId} - Cancel task (changes status to CANCELLED)
    // ========================================================================

    @DeleteMapping("/tasks/{taskId}")
    public ApiResponse<Void> cancelTask(@PathVariable Long taskId) {
        Long publisherId = userContext.getCurrentUserId();
        taskService.cancelTask(taskId, publisherId);
        return ApiResponse.success("任务已取消", null);
    }

    // ========================================================================
    // POST /api/tasks/{taskId}/apply - Apply for a task
    // ========================================================================

    @PostMapping("/tasks/{taskId}/apply")
    public ApiResponse<ApplicationDTO> applyForTask(
            @PathVariable Long taskId,
            @RequestBody @Valid TaskApplyRequest request) {
        Long applicantId = userContext.getCurrentUserId();
        ApplicationDTO application = taskService.applyForTask(taskId, applicantId, request);
        return ApiResponse.success("申请成功", application);
    }

    // ========================================================================
    // GET /api/tasks/{taskId}/applications - Get applications (publisher only)
    // ========================================================================

    @GetMapping("/tasks/{taskId}/applications")
    public ApiResponse<List<ApplicationDTO>> getApplicationsForTask(@PathVariable Long taskId) {
        Long publisherId = userContext.getCurrentUserId();
        List<ApplicationDTO> applications = taskService.getApplicationsForTask(taskId, publisherId);
        return ApiResponse.success(applications);
    }

    // ========================================================================
    // POST /api/tasks/{taskId}/applications/{appId}/award - Award application
    // ========================================================================

    @PostMapping("/tasks/{taskId}/applications/{appId}/award")
    public ApiResponse<TaskDTO> awardApplication(
            @PathVariable Long taskId,
            @PathVariable Long appId) {
        Long publisherId = userContext.getCurrentUserId();
        TaskDTO taskDTO = taskService.awardApplication(taskId, appId, publisherId);
        return ApiResponse.success("已选择中标者", taskDTO);
    }

    // ========================================================================
    // GET /api/tasks/{taskId}/messages - Get task messages
    // ========================================================================

    @GetMapping("/tasks/{taskId}/messages")
    public ApiResponse<List<MessageDTO>> getTaskMessages(@PathVariable Long taskId) {
        List<MessageDTO> messages = taskService.getTaskMessages(taskId);
        return ApiResponse.success(messages);
    }

    // ========================================================================
    // POST /api/tasks/{taskId}/messages - Send a message
    // ========================================================================

    @PostMapping("/tasks/{taskId}/messages")
    public ApiResponse<MessageDTO> sendMessage(
            @PathVariable Long taskId,
            @RequestBody @Valid MessageSendRequest request) {
        Long senderId = userContext.getCurrentUserId();
        MessageDTO message = taskService.sendMessage(taskId, senderId, request);
        return ApiResponse.success("消息发送成功", message);
    }

    // ========================================================================
    // PUT /api/tasks/{taskId}/extend - Extend deadline (publisher only)
    // ========================================================================

    @PutMapping("/tasks/{taskId}/extend")
    public ApiResponse<TaskDTO> extendDeadline(
            @PathVariable Long taskId,
            @RequestBody @Valid TaskExtendRequest request) {
        Long publisherId = userContext.getCurrentUserId();
        TaskDTO taskDTO = taskService.extendDeadline(taskId, publisherId, request);
        return ApiResponse.success("截止时间已延长", taskDTO);
    }

    // ========================================================================
    // POST /api/tasks/{taskId}/appeal - Submit appeal
    // ========================================================================

    @PostMapping("/tasks/{taskId}/appeal")
    public ApiResponse<TaskAppeal> submitAppeal(
            @PathVariable Long taskId,
            @RequestBody @Valid TaskAppealRequest request) {
        Long userId = userContext.getCurrentUserId();
        TaskAppeal appeal = taskService.submitAppeal(taskId, userId, request);
        return ApiResponse.success("申诉已提交", appeal);
    }

    // ========================================================================
    // GET /api/my/tasks - Get my published tasks
    // ========================================================================

    @GetMapping("/my/tasks")
    public ApiResponse<PageResult<TaskCardDTO>> getMyTasks(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long userId = userContext.getCurrentUserId();
        Pageable pageable = PageRequest.of(page - 1, size);
        PageResult<TaskCardDTO> result = taskService.getMyTasks(userId, status, pageable);
        return ApiResponse.success(result);
    }

    // ========================================================================
    // GET /api/my/applications - Get my application history
    // ========================================================================

    @GetMapping("/my/applications")
    public ApiResponse<PageResult<MyApplicationDTO>> getMyApplications(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long userId = userContext.getCurrentUserId();
        Pageable pageable = PageRequest.of(page - 1, size);
        PageResult<MyApplicationDTO> result = taskService.getMyApplications(userId, status, pageable);
        return ApiResponse.success(result);
    }
}
