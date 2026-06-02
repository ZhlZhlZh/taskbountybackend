package com.firstteam.taskbountyplatform.admin.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Request body for migrating tasks between categories.
 */
public class MigrateCategoryRequest {

    @NotNull
    private Long categoryId;

    @NotNull
    private Long targetCategoryId;

    @NotNull
    private List<Long> taskIds;

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public Long getTargetCategoryId() {
        return targetCategoryId;
    }

    public void setTargetCategoryId(Long targetCategoryId) {
        this.targetCategoryId = targetCategoryId;
    }

    public List<Long> getTaskIds() {
        return taskIds;
    }

    public void setTaskIds(List<Long> taskIds) {
        this.taskIds = taskIds;
    }
}
