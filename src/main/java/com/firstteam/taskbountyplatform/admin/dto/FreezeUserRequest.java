package com.firstteam.taskbountyplatform.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class FreezeUserRequest {

    @NotBlank
    @Size(max = 200)
    private String reason;

    private Integer freezeDays;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Integer getFreezeDays() {
        return freezeDays;
    }

    public void setFreezeDays(Integer freezeDays) {
        this.freezeDays = freezeDays;
    }
}
