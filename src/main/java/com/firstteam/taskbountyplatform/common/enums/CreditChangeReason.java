package com.firstteam.taskbountyplatform.common.enums;

public enum CreditChangeReason {
    COMPLETION_RATE_GOOD("完成率达标加分"),
    COMPLETION_RATE_BAD("完成率过低扣分"),
    PRAISE_RATE_GOOD("好评率达标加分"),
    PRAISE_RATE_BAD("好评率过低扣分"),
    OVERTIME("超时未交付扣分"),
    VOLUNTARY_QUIT("主动放弃任务扣分"),
    PUBLISHER_CANCEL("发布者无故下架扣分"),
    REPORT_PENALTY("举报处罚扣分"),
    FALSE_REPORT("不实举报扣分"),
    APPEAL_PENALTY("申诉裁定扣分"),
    ADMIN_RESET("管理员重置"),
    SYSTEM_ADJUST("系统自动调整"),
    FILTER_VIOLATION("敏感词过滤违规扣分");

    private final String displayName;
    CreditChangeReason(String displayName) { this.displayName = displayName; }
    public String getDisplayName() { return displayName; }
}
