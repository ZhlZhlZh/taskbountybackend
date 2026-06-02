package com.firstteam.taskbountyplatform.common.enums;

public enum ReportStatus {
    PENDING("待处理"),
    APPROVED("已核实"),
    REJECTED("已驳回");

    private final String displayName;

    ReportStatus(String displayName) { this.displayName = displayName; }
    public String getDisplayName() { return displayName; }
}
