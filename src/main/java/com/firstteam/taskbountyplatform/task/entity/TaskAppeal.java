package com.firstteam.taskbountyplatform.task.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * TaskAppeal entity - task_appeals table.
 * Represents an appeal filed by a user against a task's outcome/delivery.
 */
@Entity
@Table(name = "task_appeals")
public class TaskAppeal {

    // Status constants
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RESOLVED = "RESOLVED";

    // Decision constants
    public static final String DECISION_COMPLETED = "COMPLETED";
    public static final String DECISION_CANCELLED = "CANCELLED";
    public static final String DECISION_IN_PROGRESS = "IN_PROGRESS";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "appealer_id", nullable = false)
    private Long appealerId;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "admin_decision", nullable = true, length = 20)
    private String adminDecision;

    @Column(name = "admin_id", nullable = true)
    private Long adminId;

    @Column(name = "admin_note", nullable = true, length = 500)
    private String adminNote;

    @Column(name = "resolved_at", nullable = true)
    private LocalDateTime resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public TaskAppeal() {}

    public TaskAppeal(Long id, Long taskId, Long appealerId, String reason,
                      String status, String adminDecision, Long adminId, String adminNote,
                      LocalDateTime resolvedAt, LocalDateTime createdAt) {
        this.id = id;
        this.taskId = taskId;
        this.appealerId = appealerId;
        this.reason = reason;
        this.status = status;
        this.adminDecision = adminDecision;
        this.adminId = adminId;
        this.adminNote = adminNote;
        this.resolvedAt = resolvedAt;
        this.createdAt = createdAt;
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
        if (this.status == null) this.status = "PENDING";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getAppealerId() { return appealerId; }
    public void setAppealerId(Long appealerId) { this.appealerId = appealerId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAdminDecision() { return adminDecision; }
    public void setAdminDecision(String adminDecision) { this.adminDecision = adminDecision; }
    public Long getAdminId() { return adminId; }
    public void setAdminId(Long adminId) { this.adminId = adminId; }
    public String getAdminNote() { return adminNote; }
    public void setAdminNote(String adminNote) { this.adminNote = adminNote; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "TaskAppeal{" +
                "id=" + id + ", taskId=" + taskId + ", appealerId=" + appealerId +
                ", reason='" + reason + '\'' + ", status='" + status + '\'' +
                ", adminId=" + adminId + ", adminNote='" + adminNote + '\'' +
                ", resolvedAt=" + resolvedAt + ", createdAt=" + createdAt + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TaskAppeal that = (TaskAppeal) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
