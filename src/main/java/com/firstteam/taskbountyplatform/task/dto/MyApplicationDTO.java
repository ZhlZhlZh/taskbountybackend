package com.firstteam.taskbountyplatform.task.dto;

import java.time.LocalDateTime;

public class MyApplicationDTO {
    private Long id;
    private Long taskId;
    private String taskTitle;
    private Integer taskRewardPoints;
    private LocalDateTime appliedAt;
    private String status;

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

    public String getTaskTitle() {
        return taskTitle;
    }

    public void setTaskTitle(String taskTitle) {
        this.taskTitle = taskTitle;
    }

    public Integer getTaskRewardPoints() {
        return taskRewardPoints;
    }

    public void setTaskRewardPoints(Integer taskRewardPoints) {
        this.taskRewardPoints = taskRewardPoints;
    }

    public LocalDateTime getAppliedAt() {
        return appliedAt;
    }

    public void setAppliedAt(LocalDateTime appliedAt) {
        this.appliedAt = appliedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
