package com.firstteam.taskbountyplatform.common.enums;

public enum ReviewAuditStatus {
    PENDING("待审核"),
    APPROVED("已通过"),
    REJECTED("已拒绝"),
    TIMEOUT_REJECTED("超时自动拒绝");

    private final String displayName;
    ReviewAuditStatus(String displayName) { this.displayName = displayName; }
    public String getDisplayName() { return displayName; }
}
