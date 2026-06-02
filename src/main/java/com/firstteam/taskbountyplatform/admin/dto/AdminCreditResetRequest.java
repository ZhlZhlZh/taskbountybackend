package com.firstteam.taskbountyplatform.admin.dto;

import jakarta.validation.constraints.Size;

public class AdminCreditResetRequest {

    private boolean approved;

    @Size(max = 50)
    private String reason;

    public boolean isApproved() {
        return approved;
    }

    public void setApproved(boolean approved) {
        this.approved = approved;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
