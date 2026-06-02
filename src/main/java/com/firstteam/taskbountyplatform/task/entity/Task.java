package com.firstteam.taskbountyplatform.task.entity;

import com.firstteam.taskbountyplatform.common.enums.CampusEnum;
import com.firstteam.taskbountyplatform.common.enums.TaskStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Task entity - tasks table.
 * Represents a bounty task published on the platform.
 */
@Entity
@Table(name = "tasks")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "publisher_id", nullable = false)
    private Long publisherId;

    @Column(name = "winner_id", nullable = true)
    private Long winnerId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "category_name", nullable = false, length = 10)
    private String categoryName;

    @Column(name = "title", nullable = false, length = 30)
    private String title;

    @Column(name = "description", nullable = false, length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "campus", nullable = false, length = 30)
    private CampusEnum campus;

    @Column(name = "reward_points", nullable = false)
    private Integer rewardPoints;

    @Column(name = "deadline_minutes", nullable = false)
    private Integer deadlineMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TaskStatus status = TaskStatus.PUBLISHED;

    @Column(name = "auto_cancel_days", nullable = false)
    private Integer autoCancelDays = 14;

    @Column(name = "published_at", nullable = false)
    private LocalDateTime publishedAt;

    @Column(name = "awarded_at", nullable = true)
    private LocalDateTime awardedAt;

    @Column(name = "deadline_at", nullable = true)
    private LocalDateTime deadlineAt;

    @Column(name = "completed_at", nullable = true)
    private LocalDateTime completedAt;

    @Column(name = "cancelled_at", nullable = true)
    private LocalDateTime cancelledAt;

    @Column(name = "extend_count", nullable = false)
    private Integer extendCount = 0;

    @Column(name = "extend_total_minutes", nullable = false)
    private Integer extendTotalMinutes = 0;

    @Column(name = "appeal_at", nullable = true)
    private LocalDateTime appealAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // ========== Constructors ==========

    public Task() {
    }

    public Task(Long id, Long publisherId, Long winnerId, Long categoryId, String categoryName,
                String title, String description, CampusEnum campus, Integer rewardPoints,
                Integer deadlineMinutes, TaskStatus status, Integer autoCancelDays,
                LocalDateTime publishedAt, LocalDateTime awardedAt, LocalDateTime deadlineAt,
                LocalDateTime completedAt, LocalDateTime cancelledAt, Integer extendCount,
                Integer extendTotalMinutes, LocalDateTime appealAt, LocalDateTime createdAt,
                LocalDateTime updatedAt) {
        this.id = id;
        this.publisherId = publisherId;
        this.winnerId = winnerId;
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.title = title;
        this.description = description;
        this.campus = campus;
        this.rewardPoints = rewardPoints;
        this.deadlineMinutes = deadlineMinutes;
        this.status = status;
        this.autoCancelDays = autoCancelDays;
        this.publishedAt = publishedAt;
        this.awardedAt = awardedAt;
        this.deadlineAt = deadlineAt;
        this.completedAt = completedAt;
        this.cancelledAt = cancelledAt;
        this.extendCount = extendCount;
        this.extendTotalMinutes = extendTotalMinutes;
        this.appealAt = appealAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // ========== Lifecycle Callbacks ==========

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
        if (this.publishedAt == null) {
            this.publishedAt = now;
        }
        if (this.status == null) {
            this.status = TaskStatus.PUBLISHED;
        }
        if (this.autoCancelDays == null) {
            this.autoCancelDays = 14;
        }
        if (this.extendCount == null) {
            this.extendCount = 0;
        }
        if (this.extendTotalMinutes == null) {
            this.extendTotalMinutes = 0;
        }
        // Calculate deadlineAt from awardedAt + deadlineMinutes if both are set
        if (this.awardedAt != null && this.deadlineMinutes != null && this.deadlineAt == null) {
            this.deadlineAt = this.awardedAt.plusMinutes(this.deadlineMinutes);
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
        // Recalculate deadlineAt if awardedAt or deadlineMinutes changed
        if (this.awardedAt != null && this.deadlineMinutes != null) {
            this.deadlineAt = this.awardedAt.plusMinutes(this.deadlineMinutes);
        }
    }

    // ========== Manual Getters and Setters ==========

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

    public Long getWinnerId() {
        return winnerId;
    }

    public void setWinnerId(Long winnerId) {
        this.winnerId = winnerId;
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

    public CampusEnum getCampus() {
        return campus;
    }

    public void setCampus(CampusEnum campus) {
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

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public Integer getAutoCancelDays() {
        return autoCancelDays;
    }

    public void setAutoCancelDays(Integer autoCancelDays) {
        this.autoCancelDays = autoCancelDays;
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

    public Integer getExtendTotalMinutes() {
        return extendTotalMinutes;
    }

    public void setExtendTotalMinutes(Integer extendTotalMinutes) {
        this.extendTotalMinutes = extendTotalMinutes;
    }

    public LocalDateTime getAppealAt() {
        return appealAt;
    }

    public void setAppealAt(LocalDateTime appealAt) {
        this.appealAt = appealAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    // ========== toString ==========

    @Override
    public String toString() {
        return "Task{" +
                "id=" + id +
                ", publisherId=" + publisherId +
                ", winnerId=" + winnerId +
                ", categoryId=" + categoryId +
                ", categoryName='" + categoryName + '\'' +
                ", title='" + title + '\'' +
                ", description='" + description + '\'' +
                ", campus=" + campus +
                ", rewardPoints=" + rewardPoints +
                ", deadlineMinutes=" + deadlineMinutes +
                ", status=" + status +
                ", autoCancelDays=" + autoCancelDays +
                ", publishedAt=" + publishedAt +
                ", awardedAt=" + awardedAt +
                ", deadlineAt=" + deadlineAt +
                ", completedAt=" + completedAt +
                ", cancelledAt=" + cancelledAt +
                ", extendCount=" + extendCount +
                ", extendTotalMinutes=" + extendTotalMinutes +
                ", appealAt=" + appealAt +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }

    // ========== equals and hashCode ==========

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Task task = (Task) o;
        return Objects.equals(id, task.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
