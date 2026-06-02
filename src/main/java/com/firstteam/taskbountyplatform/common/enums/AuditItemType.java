package com.firstteam.taskbountyplatform.common.enums;

public enum AuditItemType {
    AVATAR("头像"),
    NICKNAME("昵称"),
    ANNOUNCEMENT("公告栏"),
    EMAIL("邮箱"),
    PHONE("手机号");

    private final String displayName;
    AuditItemType(String displayName) { this.displayName = displayName; }
    public String getDisplayName() { return displayName; }
}
