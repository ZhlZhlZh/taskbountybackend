package com.firstteam.taskbountyplatform.task.dto;

import java.time.LocalDateTime;

public class ApplicationDTO {
    private Long id;
    private Long taskId;
    private Long applicantId;
    private String applicantNickname;
    private Integer applicantCreditScore;
    private String applyReason;
    private String status;
    private LocalDateTime appliedAt;
    private String applicantCompletionRate;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public Long getApplicantId() {
        return applicantId;
    }

    public void setApplicantId(Long applicantId) {
        this.applicantId = applicantId;
    }

    public String getApplicantNickname() {
        return applicantNickname;
    }

    public void setApplicantNickname(String applicantNickname) {
        this.applicantNickname = applicantNickname;
    }

    public Integer getApplicantCreditScore() {
        return applicantCreditScore;
    }

    public void setApplicantCreditScore(Integer applicantCreditScore) {
        this.applicantCreditScore = applicantCreditScore;
    }

    public String getApplyReason() {
        return applyReason;
    }

    public void setApplyReason(String applyReason) {
        this.applyReason = applyReason;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getAppliedAt() {
        return appliedAt;
    }

    public void setAppliedAt(LocalDateTime appliedAt) {
        this.appliedAt = appliedAt;
    }

    public String getApplicantCompletionRate() {
        return applicantCompletionRate;
    }

    public void setApplicantCompletionRate(String applicantCompletionRate) {
        this.applicantCompletionRate = applicantCompletionRate;
    }
}
