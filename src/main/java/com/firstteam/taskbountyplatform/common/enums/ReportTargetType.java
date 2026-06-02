package com.firstteam.taskbountyplatform.common.enums;

public enum ReportTargetType {
    TASK("任务"),
    USER("用户");

    private final String displayName;
    ReportTargetType(String displayName) { this.displayName = displayName; }
    public String getDisplayName() { return displayName; }
}
