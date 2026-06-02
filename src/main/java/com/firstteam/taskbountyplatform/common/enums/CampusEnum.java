package com.firstteam.taskbountyplatform.common.enums;

public enum CampusEnum {
    LIANGXIANG("良乡校区"),
    ZHONGGUANCUN("中关村校区"),
    BOTH("两校区往返");

    private final String displayName;
    CampusEnum(String displayName) { this.displayName = displayName; }
    public String getDisplayName() { return displayName; }
}
