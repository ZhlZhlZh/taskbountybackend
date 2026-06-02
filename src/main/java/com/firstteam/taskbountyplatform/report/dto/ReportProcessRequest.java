package com.firstteam.taskbountyplatform.report.dto;

import jakarta.validation.constraints.Size;

public class ReportProcessRequest {

    @Size(max = 200)
    private String adminNote;

    private Integer penaltyDays;

    private Integer creditPenalty;

    public String getAdminNote() { return adminNote; }
    public void setAdminNote(String adminNote) { this.adminNote = adminNote; }
    public Integer getPenaltyDays() { return penaltyDays; }
    public void setPenaltyDays(Integer penaltyDays) { this.penaltyDays = penaltyDays; }
    public Integer getCreditPenalty() { return creditPenalty; }
    public void setCreditPenalty(Integer creditPenalty) { this.creditPenalty = creditPenalty; }
}
