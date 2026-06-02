package com.firstteam.taskbountyplatform.common.enums;

public enum PointFlowType {
    INCOME("收入"),
    EXPENSE("支出"),
    FREEZE("冻结"),
    UNFREEZE("解冻");

    private final String displayName;
    PointFlowType(String displayName) { this.displayName = displayName; }
    public String getDisplayName() { return displayName; }
}
