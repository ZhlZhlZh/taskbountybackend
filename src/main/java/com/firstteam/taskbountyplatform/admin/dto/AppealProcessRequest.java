package com.firstteam.taskbountyplatform.admin.dto;

import jakarta.validation.constraints.Size;

public class AppealProcessRequest {

    private String decision;

    @Size(max = 200)
    private String note;

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
