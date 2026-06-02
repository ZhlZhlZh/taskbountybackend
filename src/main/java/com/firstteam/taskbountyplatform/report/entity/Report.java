package com.firstteam.taskbountyplatform.report.entity;

import com.firstteam.taskbountyplatform.common.enums.ReportStatus;
import com.firstteam.taskbountyplatform.common.enums.ReportTargetType;
import com.firstteam.taskbountyplatform.common.enums.ReportType;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Report entity - reports table.
 * Represents a user report against a task, user, or delivery.
 */
@Entity
@Table(name = "reports")
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private ReportTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "reporter_id", nullable = false)
    private Long reporterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 30)
    private ReportType reportType;

    @Column(name = "evidence", length = 100)
    private String evidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReportStatus status = ReportStatus.PENDING;

    @Column(name = "admin_id", nullable = true)
    private Long adminId;

    @Column(name = "admin_note", length = 200, nullable = true)
    private String adminNote;

    @Column(name = "penalty_days", nullable = true)
    private Integer penaltyDays;

    @Column(name = "credit_penalty", nullable = true)
    private Integer creditPenalty;

    @Column(name = "processed_at", nullable = true)
    private LocalDateTime processedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // ========== Constructors ==========

    public Report() {
    }

    public Report(Long id, ReportTargetType targetType, Long targetId, Long reporterId,
                  ReportType reportType, String evidence, ReportStatus status, Long adminId,
                  String adminNote, Integer penaltyDays, Integer creditPenalty,
                  LocalDateTime processedAt, LocalDateTime createdAt) {
        this.id = id;
        this.targetType = targetType;
        this.targetId = targetId;
        this.reporterId = reporterId;
        this.reportType = reportType;
        this.evidence = evidence;
        this.status = status;
        this.adminId = adminId;
        this.adminNote = adminNote;
        this.penaltyDays = penaltyDays;
        this.creditPenalty = creditPenalty;
        this.processedAt = processedAt;
        this.createdAt = createdAt;
    }

    // ========== Lifecycle Callbacks ==========

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.status == null) {
            this.status = ReportStatus.PENDING;
        }
    }

    // ========== Manual Getters and Setters ==========

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ReportTargetType getTargetType() {
        return targetType;
    }

    public void setTargetType(ReportTargetType targetType) {
        this.targetType = targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public void setTargetId(Long targetId) {
        this.targetId = targetId;
    }

    public Long getReporterId() {
        return reporterId;
    }

    public void setReporterId(Long reporterId) {
        this.reporterId = reporterId;
    }

    public ReportType getReportType() {
        return reportType;
    }

    public void setReportType(ReportType reportType) {
        this.reportType = reportType;
    }

    public String getEvidence() {
        return evidence;
    }

    public void setEvidence(String evidence) {
        this.evidence = evidence;
    }

    public ReportStatus getStatus() {
        return status;
    }

    public void setStatus(ReportStatus status) {
        this.status = status;
    }

    public Long getAdminId() {
        return adminId;
    }

    public void setAdminId(Long adminId) {
        this.adminId = adminId;
    }

    public String getAdminNote() {
        return adminNote;
    }

    public void setAdminNote(String adminNote) {
        this.adminNote = adminNote;
    }

    public Integer getPenaltyDays() {
        return penaltyDays;
    }

    public void setPenaltyDays(Integer penaltyDays) {
        this.penaltyDays = penaltyDays;
    }

    public Integer getCreditPenalty() {
        return creditPenalty;
    }

    public void setCreditPenalty(Integer creditPenalty) {
        this.creditPenalty = creditPenalty;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
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
        return "Report{" +
                "id=" + id +
                ", targetType=" + targetType +
                ", targetId=" + targetId +
                ", reporterId=" + reporterId +
                ", reportType=" + reportType +
                ", evidence='" + evidence + '\'' +
                ", status=" + status +
                ", adminId=" + adminId +
                ", adminNote='" + adminNote + '\'' +
                ", penaltyDays=" + penaltyDays +
                ", creditPenalty=" + creditPenalty +
                ", processedAt=" + processedAt +
                ", createdAt=" + createdAt +
                '}';
    }

    // ========== equals and hashCode ==========

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Report report = (Report) o;
        return Objects.equals(id, report.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
