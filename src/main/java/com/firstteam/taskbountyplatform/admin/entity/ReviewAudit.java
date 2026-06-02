package com.firstteam.taskbountyplatform.admin.entity;

import com.firstteam.taskbountyplatform.common.enums.AuditItemType;
import com.firstteam.taskbountyplatform.common.enums.ReviewAuditStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * ReviewAudit entity - review_audits table.
 * Represents an admin review audit for user profile changes.
 * Timeout is calculated as submittedAt + 24 hours.
 */
@Entity
@Table(name = "review_audits")
public class ReviewAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "applicant_id", nullable = false)
    private Long applicantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "audit_type", nullable = false, length = 20)
    private AuditItemType auditType;

    @Column(name = "old_value", nullable = false, length = 500)
    private String oldValue;

    @Column(name = "new_value", nullable = false, length = 500)
    private String newValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReviewAuditStatus status = ReviewAuditStatus.PENDING;

    @Column(name = "admin_id", nullable = true)
    private Long adminId;

    @Column(name = "reject_reason", length = 200, nullable = true)
    private String rejectReason;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @Column(name = "processed_at", nullable = true)
    private LocalDateTime processedAt;

    @Column(name = "timeout_at", nullable = false)
    private LocalDateTime timeoutAt;

    // ========== Constructors ==========

    public ReviewAudit() {
    }

    public ReviewAudit(Long id, Long applicantId, AuditItemType auditType, String oldValue,
                       String newValue, ReviewAuditStatus status, Long adminId,
                       String rejectReason, LocalDateTime submittedAt,
                       LocalDateTime processedAt, LocalDateTime timeoutAt) {
        this.id = id;
        this.applicantId = applicantId;
        this.auditType = auditType;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.status = status;
        this.adminId = adminId;
        this.rejectReason = rejectReason;
        this.submittedAt = submittedAt;
        this.processedAt = processedAt;
        this.timeoutAt = timeoutAt;
    }

    // ========== Lifecycle Callbacks ==========

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.submittedAt == null) {
            this.submittedAt = now;
        }
        if (this.timeoutAt == null) {
            this.timeoutAt = this.submittedAt.plusHours(24);
        }
        if (this.status == null) {
            this.status = ReviewAuditStatus.PENDING;
        }
    }

    // ========== Manual Getters and Setters ==========

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getApplicantId() {
        return applicantId;
    }

    public void setApplicantId(Long applicantId) {
        this.applicantId = applicantId;
    }

    public AuditItemType getAuditType() {
        return auditType;
    }

    public void setAuditType(AuditItemType auditType) {
        this.auditType = auditType;
    }

    public String getOldValue() {
        return oldValue;
    }

    public void setOldValue(String oldValue) {
        this.oldValue = oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public void setNewValue(String newValue) {
        this.newValue = newValue;
    }

    public ReviewAuditStatus getStatus() {
        return status;
    }

    public void setStatus(ReviewAuditStatus status) {
        this.status = status;
    }

    public Long getAdminId() {
        return adminId;
    }

    public void setAdminId(Long adminId) {
        this.adminId = adminId;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }

    public LocalDateTime getTimeoutAt() {
        return timeoutAt;
    }

    public void setTimeoutAt(LocalDateTime timeoutAt) {
        this.timeoutAt = timeoutAt;
    }

    // ========== toString ==========

    @Override
    public String toString() {
        return "ReviewAudit{" +
                "id=" + id +
                ", applicantId=" + applicantId +
                ", auditType=" + auditType +
                ", oldValue='" + oldValue + '\'' +
                ", newValue='" + newValue + '\'' +
                ", status=" + status +
                ", adminId=" + adminId +
                ", rejectReason='" + rejectReason + '\'' +
                ", submittedAt=" + submittedAt +
                ", processedAt=" + processedAt +
                ", timeoutAt=" + timeoutAt +
                '}';
    }

    // ========== equals and hashCode ==========

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReviewAudit that = (ReviewAudit) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
