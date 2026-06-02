package com.firstteam.taskbountyplatform.credit.entity;

import com.firstteam.taskbountyplatform.common.enums.CreditChangeReason;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * CreditRecord entity - credit_records table.
 * Represents a record of a user's credit score change.
 */
@Entity
@Table(name = "credit_records")
public class CreditRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "task_id", nullable = true)
    private Long taskId;

    @Column(name = "change_score", nullable = false)
    private Integer changeScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_type", nullable = false, length = 30)
    private CreditChangeReason reasonType;

    @Column(name = "before_score", nullable = false)
    private Integer beforeScore;

    @Column(name = "after_score", nullable = false)
    private Integer afterScore;

    @Column(name = "description", nullable = true, length = 255)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // ========== Constructors ==========

    public CreditRecord() {
    }

    public CreditRecord(Long id, Long userId, Long taskId, Integer changeScore,
                        CreditChangeReason reasonType, Integer beforeScore,
                        Integer afterScore, String description, LocalDateTime createdAt) {
        this.id = id;
        this.userId = userId;
        this.taskId = taskId;
        this.changeScore = changeScore;
        this.reasonType = reasonType;
        this.beforeScore = beforeScore;
        this.afterScore = afterScore;
        this.description = description;
        this.createdAt = createdAt;
    }

    // ========== Lifecycle Callbacks ==========

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    // ========== Manual Getters and Setters ==========

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public Integer getChangeScore() {
        return changeScore;
    }

    public void setChangeScore(Integer changeScore) {
        this.changeScore = changeScore;
    }

    public CreditChangeReason getReasonType() {
        return reasonType;
    }

    public void setReasonType(CreditChangeReason reasonType) {
        this.reasonType = reasonType;
    }

    public Integer getBeforeScore() {
        return beforeScore;
    }

    public void setBeforeScore(Integer beforeScore) {
        this.beforeScore = beforeScore;
    }

    public Integer getAfterScore() {
        return afterScore;
    }

    public void setAfterScore(Integer afterScore) {
        this.afterScore = afterScore;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    // ========== toString ==========

    @Override
    public String toString() {
        return "CreditRecord{" +
                "id=" + id +
                ", userId=" + userId +
                ", taskId=" + taskId +
                ", changeScore=" + changeScore +
                ", reasonType=" + reasonType +
                ", beforeScore=" + beforeScore +
                ", afterScore=" + afterScore +
                ", description='" + description + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }

    // ========== equals and hashCode ==========

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CreditRecord that = (CreditRecord) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
