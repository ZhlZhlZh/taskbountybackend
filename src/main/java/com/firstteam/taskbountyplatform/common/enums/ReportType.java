package com.firstteam.taskbountyplatform.common.enums;

public enum ReportType {
    PORNOGRAPHY("涉黄"),
    VIOLENCE("涉暴"),
    FRAUD("诈骗"),
    OTHER("其他");

    private final String displayName;
    ReportType(String displayName) { this.displayName = displayName; }
    public String getDisplayName() { return displayName; }
}
