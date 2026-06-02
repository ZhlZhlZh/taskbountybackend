package com.firstteam.taskbountyplatform.admin.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public class CategoryMigrateRequest {

    @NotNull
    private Long targetCategoryId;

    private List<Long> taskIds;

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
