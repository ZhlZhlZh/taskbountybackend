package com.firstteam.taskbountyplatform.common.enums;

public enum DeliveryStatus {
    SUBMITTED("已提交"),
    ACCEPTED("已确认"),
    REJECTED("已退回");

    private final String displayName;
    DeliveryStatus(String displayName) { this.displayName = displayName; }
    public String getDisplayName() { return displayName; }
}
