package com.firstteam.taskbountyplatform.common.enums;

public enum AccountRole {
    USER("普通用户"),
    ADMIN("管理员");

    private final String displayName;
    AccountRole(String displayName) { this.displayName = displayName; }
    public String getDisplayName() { return displayName; }
}
