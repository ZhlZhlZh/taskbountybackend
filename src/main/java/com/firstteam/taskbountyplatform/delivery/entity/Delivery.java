package com.firstteam.taskbountyplatform.delivery.entity;

import com.firstteam.taskbountyplatform.common.enums.DeliveryStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Delivery entity - deliveries table.
 * Represents a worker's submission/delivery for a task.
 */
@Entity
@Table(name = "deliveries")
public class Delivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "worker_id", nullable = false)
    private Long workerId;

    @Column(name = "description", length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DeliveryStatus status = DeliveryStatus.SUBMITTED;

    @Column(name = "submit_time", nullable = false)
    private LocalDateTime submitTime;

    @Column(name = "reject_count", nullable = false)
    private Integer rejectCount = 0;

    @Column(name = "rejected_at", nullable = true)
    private LocalDateTime rejectedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // ========== Constructors ==========

    public Delivery() {
    }

    public Delivery(Long id, Long taskId, Long workerId, String description,
                    DeliveryStatus status, LocalDateTime submitTime, Integer rejectCount,
                    LocalDateTime rejectedAt, LocalDateTime createdAt) {
        this.id = id;
        this.taskId = taskId;
        this.workerId = workerId;
        this.description = description;
        this.status = status;
        this.submitTime = submitTime;
        this.rejectCount = rejectCount;
        this.rejectedAt = rejectedAt;
        this.createdAt = createdAt;
    }

    // ========== Lifecycle Callbacks ==========

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.submitTime == null) {
            this.submitTime = now;
        }
        if (this.status == null) {
            this.status = DeliveryStatus.SUBMITTED;
        }
        if (this.rejectCount == null) {
            this.rejectCount = 0;
        }
    }

    // ========== Manual Getters and Setters ==========

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

    public Long getWorkerId() {
        return workerId;
    }

    public void setWorkerId(Long workerId) {
        this.workerId = workerId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public DeliveryStatus getStatus() {
        return status;
    }

    public void setStatus(DeliveryStatus status) {
        this.status = status;
    }

    public LocalDateTime getSubmitTime() {
        return submitTime;
    }

    public void setSubmitTime(LocalDateTime submitTime) {
        this.submitTime = submitTime;
    }

    public Integer getRejectCount() {
        return rejectCount;
    }

    public void setRejectCount(Integer rejectCount) {
        this.rejectCount = rejectCount;
    }

    public LocalDateTime getRejectedAt() {
        return rejectedAt;
    }

    public void setRejectedAt(LocalDateTime rejectedAt) {
        this.rejectedAt = rejectedAt;
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
        return "Delivery{" +
                "id=" + id +
                ", taskId=" + taskId +
                ", workerId=" + workerId +
                ", description='" + description + '\'' +
                ", status=" + status +
                ", submitTime=" + submitTime +
                ", rejectCount=" + rejectCount +
                ", rejectedAt=" + rejectedAt +
                ", createdAt=" + createdAt +
                '}';
    }

    // ========== equals and hashCode ==========

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Delivery delivery = (Delivery) o;
        return Objects.equals(id, delivery.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
