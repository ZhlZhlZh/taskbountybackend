package com.firstteam.taskbountyplatform.credit.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * CreditRuleConfig entity - credit_rule_configs table.
 * Represents a credit scoring rule configuration.
 */
@Entity
@Table(name = "credit_rule_configs")
public class CreditRuleConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_key", unique = true, nullable = false, length = 100)
    private String ruleKey;

    @Column(name = "rule_name", nullable = false, length = 100)
    private String ruleName;

    @Column(name = "threshold_value", nullable = false, length = 100)
    private String thresholdValue;

    @Column(name = "score_delta", nullable = false)
    private Integer scoreDelta;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // ========== Constructors ==========

    public CreditRuleConfig() {
    }

    public CreditRuleConfig(Long id, String ruleKey, String ruleName, String thresholdValue,
                            Integer scoreDelta, Boolean enabled, LocalDateTime updatedAt) {
        this.id = id;
        this.ruleKey = ruleKey;
        this.ruleName = ruleName;
        this.thresholdValue = thresholdValue;
        this.scoreDelta = scoreDelta;
        this.enabled = enabled;
        this.updatedAt = updatedAt;
    }

    // ========== Lifecycle Callbacks ==========

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
        if (this.enabled == null) {
            this.enabled = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ========== Manual Getters and Setters ==========

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRuleKey() {
        return ruleKey;
    }

    public void setRuleKey(String ruleKey) {
        this.ruleKey = ruleKey;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public String getThresholdValue() {
        return thresholdValue;
    }

    public void setThresholdValue(String thresholdValue) {
        this.thresholdValue = thresholdValue;
    }

    public Integer getScoreDelta() {
        return scoreDelta;
    }

    public void setScoreDelta(Integer scoreDelta) {
        this.scoreDelta = scoreDelta;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
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
        return "CreditRuleConfig{" +
                "id=" + id +
                ", ruleKey='" + ruleKey + '\'' +
                ", ruleName='" + ruleName + '\'' +
                ", thresholdValue='" + thresholdValue + '\'' +
                ", scoreDelta=" + scoreDelta +
                ", enabled=" + enabled +
                ", updatedAt=" + updatedAt +
                '}';
    }

    // ========== equals and hashCode ==========

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CreditRuleConfig that = (CreditRuleConfig) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
