package com.firstteam.taskbountyplatform.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class TaskApplyRequest {

    @NotBlank
    @Size(min = 10, max = 200)
    private String applyReason;

    public String getApplyReason() {
        return applyReason;
    }

    public void setApplyReason(String applyReason) {
        this.applyReason = applyReason;
    }
}
