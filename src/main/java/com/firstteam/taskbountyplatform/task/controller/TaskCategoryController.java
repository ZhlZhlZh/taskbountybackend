package com.firstteam.taskbountyplatform.task.controller;

import com.firstteam.taskbountyplatform.auth.security.UserContext;
import com.firstteam.taskbountyplatform.common.exception.BusinessException;
import com.firstteam.taskbountyplatform.common.response.ApiResponse;
import com.firstteam.taskbountyplatform.task.entity.TaskCategory;
import com.firstteam.taskbountyplatform.task.repository.TaskCategoryRepository;
import com.firstteam.taskbountyplatform.task.repository.TaskRepository;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * TaskCategoryController - REST API for task categories.
 * Public read endpoint and admin management endpoints.
 */
@RestController
@RequestMapping("/api")
public class TaskCategoryController {

    private final TaskCategoryRepository categoryRepository;
    private final TaskRepository taskRepository;
    private final UserContext userContext;

    public TaskCategoryController(TaskCategoryRepository categoryRepository,
                                   TaskRepository taskRepository,
                                   UserContext userContext) {
        this.categoryRepository = categoryRepository;
        this.taskRepository = taskRepository;
        this.userContext = userContext;
    }

    // ========================================================================
    // GET /api/public/categories - List all enabled categories (no auth)
    // ========================================================================

    @GetMapping("/public/categories")
    public ApiResponse<List<TaskCategory>> listEnabledCategories() {
        List<TaskCategory> categories = categoryRepository.findByEnabledTrueOrderBySortOrderAsc();
        return ApiResponse.success(categories);
    }

    // ========================================================================
    // POST /api/admin/categories - Create a category (admin)
    // ========================================================================

    @PostMapping("/admin/categories")
    public ApiResponse<TaskCategory> createCategory(@RequestBody @Valid TaskCategory category) {
        // Check if category name already exists
        if (categoryRepository.existsByName(category.getName())) {
            throw new BusinessException(400, "分类名称已存在: " + category.getName());
        }

        TaskCategory saved = categoryRepository.save(category);
        return ApiResponse.success("分类创建成功", saved);
    }

    // ========================================================================
    // PUT /api/admin/categories/{id} - Update a category (admin)
    // ========================================================================

    @PutMapping("/admin/categories/{id}")
    public ApiResponse<TaskCategory> updateCategory(
            @PathVariable Long id,
            @RequestBody @Valid TaskCategory updateRequest) {
        TaskCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "分类不存在"));

        // Check if new name conflicts with another category
        if (updateRequest.getName() != null
                && !updateRequest.getName().equals(category.getName())
                && categoryRepository.existsByName(updateRequest.getName())) {
            throw new BusinessException(400, "分类名称已存在: " + updateRequest.getName());
        }

        if (updateRequest.getName() != null) {
            category.setName(updateRequest.getName());
        }
        if (updateRequest.getSortOrder() != null) {
            category.setSortOrder(updateRequest.getSortOrder());
        }
        if (updateRequest.getEnabled() != null) {
            category.setEnabled(updateRequest.getEnabled());
        }

        TaskCategory saved = categoryRepository.save(category);
        return ApiResponse.success("分类更新成功", saved);
    }

    // ========================================================================
    // DELETE /api/admin/categories/{id} - Delete a category (admin)
    // ========================================================================

    @DeleteMapping("/admin/categories/{id}")
    public ApiResponse<Void> deleteCategory(@PathVariable Long id) {
        TaskCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "分类不存在"));

        // Validate no in-progress tasks in this category
        long activeTaskCount = taskRepository.countByCategoryIdAndStatus(
                id, com.firstteam.taskbountyplatform.common.enums.TaskStatus.IN_PROGRESS);
        if (activeTaskCount > 0) {
            throw new BusinessException(400,
                    "该分类下有 " + activeTaskCount + " 个进行中的任务，无法删除");
        }

        // Also check for PUBLISHED tasks
        long publishedTaskCount = taskRepository.countByCategoryIdAndStatus(
                id, com.firstteam.taskbountyplatform.common.enums.TaskStatus.PUBLISHED);
        if (publishedTaskCount > 0) {
            throw new BusinessException(400,
                    "该分类下有 " + publishedTaskCount + " 个已发布的任务，无法删除");
        }

        categoryRepository.delete(category);
        return ApiResponse.success("分类已删除", null);
    }
}
