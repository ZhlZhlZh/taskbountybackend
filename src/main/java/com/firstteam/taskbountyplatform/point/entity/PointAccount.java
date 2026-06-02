package com.firstteam.taskbountyplatform.point.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * PointAccount entity - point_accounts table.
 * Represents a user's point balance account.
 * Uses optimistic locking via @Version.
 */
@Entity
@Table(name = "point_accounts")
public class PointAccount {

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "available_points", nullable = false)
    private Integer availablePoints = 1000;

    @Column(name = "frozen_points", nullable = false)
    private Integer frozenPoints = 0;

    @Column(name = "total_income", nullable = false)
    private Integer totalIncome = 0;

    @Column(name = "total_expense", nullable = false)
    private Integer totalExpense = 0;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    // ========== Constructors ==========

    public PointAccount() {
    }

    public PointAccount(Long userId, Integer availablePoints, Integer frozenPoints,
                        Integer totalIncome, Integer totalExpense, LocalDateTime updatedAt) {
        this.userId = userId;
        this.availablePoints = availablePoints;
        this.frozenPoints = frozenPoints;
        this.totalIncome = totalIncome;
        this.totalExpense = totalExpense;
        this.updatedAt = updatedAt;
    }

    // ========== Lifecycle Callbacks ==========

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
        if (this.availablePoints == null) {
            this.availablePoints = 1000;
        }
        if (this.frozenPoints == null) {
            this.frozenPoints = 0;
        }
        if (this.totalIncome == null) {
            this.totalIncome = 0;
        }
        if (this.totalExpense == null) {
            this.totalExpense = 0;
        }
        if (this.version == null) {
            this.version = 0L;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ========== Manual Getters and Setters ==========

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Integer getAvailablePoints() {
        return availablePoints;
    }

    public void setAvailablePoints(Integer availablePoints) {
        this.availablePoints = availablePoints;
    }

    public Integer getFrozenPoints() {
        return frozenPoints;
    }

    public void setFrozenPoints(Integer frozenPoints) {
        this.frozenPoints = frozenPoints;
    }

    public Integer getTotalIncome() {
        return totalIncome;
    }

    public void setTotalIncome(Integer totalIncome) {
        this.totalIncome = totalIncome;
    }

    public Integer getTotalExpense() {
        return totalExpense;
    }

    public void setTotalExpense(Integer totalExpense) {
        this.totalExpense = totalExpense;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    // ========== toString ==========

    @Override
    public String toString() {
        return "PointAccount{" +
                "userId=" + userId +
                ", availablePoints=" + availablePoints +
                ", frozenPoints=" + frozenPoints +
                ", totalIncome=" + totalIncome +
                ", totalExpense=" + totalExpense +
                ", updatedAt=" + updatedAt +
                ", version=" + version +
                '}';
    }

    // ========== equals and hashCode ==========

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PointAccount that = (PointAccount) o;
        return Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId);
    }
}
