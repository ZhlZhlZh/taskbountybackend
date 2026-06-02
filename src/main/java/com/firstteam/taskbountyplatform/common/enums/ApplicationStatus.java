package com.firstteam.taskbountyplatform.common.enums;

/**
 * 任务申请状态
 */
public enum ApplicationStatus {
    REVIEWING("审核中"),
    AWARDED("已中标"),
    REJECTED("已落选"),
    CANCELLED("已取消");

    private final String displayName;

    ApplicationStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
