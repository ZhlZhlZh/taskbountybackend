package com.firstteam.taskbountyplatform.admin.dto;

import jakarta.validation.constraints.Size;

public class CreditResetRequest {

    @Size(max = 50)
    private String reason;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
