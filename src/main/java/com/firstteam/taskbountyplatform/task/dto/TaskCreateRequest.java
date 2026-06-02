package com.firstteam.taskbountyplatform.task.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class TaskCreateRequest {

    @NotBlank
    @Size(max = 30)
    private String title;

    @NotBlank
    @Size(max = 2000)
    private String description;

    @NotNull
    private Long categoryId;

    private String campus;

    @Min(30)
    @Max(43200)
    private Integer deadlineMinutes;

    @Min(1)
    @Max(5000)
    private Integer rewardPoints;

    @Min(1)
    @Max(14)
    private Integer autoCancelDays;

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

    public Integer getAutoCancelDays() {
        return autoCancelDays;
    }

    public void setAutoCancelDays(Integer autoCancelDays) {
        this.autoCancelDays = autoCancelDays;
    }
}
