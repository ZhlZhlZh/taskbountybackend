package com.firstteam.taskbountyplatform.task.dto;

import jakarta.validation.constraints.Min;

public class TaskExtendRequest {

    @Min(1)
    private Integer extendMinutes;

    public Integer getExtendMinutes() {
        return extendMinutes;
    }

    public void setExtendMinutes(Integer extendMinutes) {
        this.extendMinutes = extendMinutes;
    }
}
