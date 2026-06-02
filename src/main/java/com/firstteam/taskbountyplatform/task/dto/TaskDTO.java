package com.firstteam.taskbountyplatform.task.dto;

import com.firstteam.taskbountyplatform.file.dto.FileDTO;

import java.time.LocalDateTime;
import java.util.List;

public class TaskDTO {
    private Long id;
    private Long publisherId;
    private String publisherNickname;
    private Integer publisherCreditScore;
    private Long winnerId;
    private String winnerNickname;
    private Long categoryId;
    private String categoryName;
    private String title;
    private String description;
    private String campus;
    private Integer rewardPoints;
    private Integer deadlineMinutes;
    private String status;
    private LocalDateTime publishedAt;
    private LocalDateTime awardedAt;
    private LocalDateTime deadlineAt;
    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;
    private Integer extendCount;
    private List<FileDTO> files;
    private int applicationCount;
    private List<ApplicationDTO> applications;
    private String myApplicationStatus;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPublisherId() {
        return publisherId;
    }

    public void setPublisherId(Long publisherId) {
        this.publisherId = publisherId;
    }

    public String getPublisherNickname() {
        return publisherNickname;
    }

    public void setPublisherNickname(String publisherNickname) {
        this.publisherNickname = publisherNickname;
    }

    public Integer getPublisherCreditScore() {
        return publisherCreditScore;
    }

    public void setPublisherCreditScore(Integer publisherCreditScore) {
        this.publisherCreditScore = publisherCreditScore;
    }

    public Long getWinnerId() {
        return winnerId;
    }

    public void setWinnerId(Long winnerId) {
        this.winnerId = winnerId;
    }

    public String getWinnerNickname() {
        return winnerNickname;
    }

    public void setWinnerNickname(String winnerNickname) {
        this.winnerNickname = winnerNickname;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCampus() {
        return campus;
    }

    public void setCampus(String campus) {
        this.campus = campus;
    }

    public Integer getRewardPoints() {
        return rewardPoints;
    }

    public void setRewardPoints(Integer rewardPoints) {
        this.rewardPoints = rewardPoints;
    }

    public Integer getDeadlineMinutes() {
        return deadlineMinutes;
    }

    public void setDeadlineMinutes(Integer deadlineMinutes) {
        this.deadlineMinutes = deadlineMinutes;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public LocalDateTime getAwardedAt() {
        return awardedAt;
    }

    public void setAwardedAt(LocalDateTime awardedAt) {
        this.awardedAt = awardedAt;
    }

    public LocalDateTime getDeadlineAt() {
        return deadlineAt;
    }

    public void setDeadlineAt(LocalDateTime deadlineAt) {
        this.deadlineAt = deadlineAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(LocalDateTime cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public Integer getExtendCount() {
        return extendCount;
    }

    public void setExtendCount(Integer extendCount) {
        this.extendCount = extendCount;
    }

    public List<FileDTO> getFiles() {
        return files;
    }

    public void setFiles(List<FileDTO> files) {
        this.files = files;
    }

    public int getApplicationCount() {
        return applicationCount;
    }

    public void setApplicationCount(int applicationCount) {
        this.applicationCount = applicationCount;
    }

    public List<ApplicationDTO> getApplications() {
        return applications;
    }

    public void setApplications(List<ApplicationDTO> applications) {
        this.applications = applications;
    }

    public String getMyApplicationStatus() {
        return myApplicationStatus;
    }

    public void setMyApplicationStatus(String myApplicationStatus) {
        this.myApplicationStatus = myApplicationStatus;
    }
}
