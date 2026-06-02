package com.firstteam.taskbountyplatform.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AppealProcessRequest {

    @NotBlank
    private String decision;

    @Size(max = 500)
    private String adminNote;

    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
    public String getAdminNote() { return adminNote; }
    public void setAdminNote(String adminNote) { this.adminNote = adminNote; }
}
