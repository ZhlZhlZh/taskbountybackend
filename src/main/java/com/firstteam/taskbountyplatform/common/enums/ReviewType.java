package com.firstteam.taskbountyplatform.common.enums;

public enum ReviewType {
    PUBLISHER_TO_WORKER("发布者评价接单者"),
    WORKER_TO_PUBLISHER("接单者评价发布者");

    private final String displayName;
    ReviewType(String displayName) { this.displayName = displayName; }
    public String getDisplayName() { return displayName; }
}
