package com.firstteam.taskbountyplatform.task.dto;

public class TaskUpdateRequest {
    private String title;
    private String description;
    private Long categoryId;
    private String campus;
    private Integer deadlineMinutes;
    private Integer rewardPoints;

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

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getCampus() {
        return campus;
    }

    public void setCampus(String campus) {
        this.campus = campus;
    }

    public Integer getDeadlineMinutes() {
        return deadlineMinutes;
    }

    public void setDeadlineMinutes(Integer deadlineMinutes) {
        this.deadlineMinutes = deadlineMinutes;
    }

    public Integer getRewardPoints() {
        return rewardPoints;
    }

    public void setRewardPoints(Integer rewardPoints) {
        this.rewardPoints = rewardPoints;
    }
}
