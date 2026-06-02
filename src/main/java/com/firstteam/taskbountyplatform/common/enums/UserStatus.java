package com.firstteam.taskbountyplatform.common.enums;

/**
 * 用户账户状态
 */
public enum UserStatus {
    NORMAL("正常"),
    FROZEN("冻结");

    private final String displayName;

    UserStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
