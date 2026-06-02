package com.firstteam.taskbountyplatform.common.enums;

public enum NotificationType {
    TASK_AWARDED("任务中标"),
    TASK_REJECTED("任务落选"),
    TASK_CANCELLED("任务取消"),
    TASK_COMPLETED("任务完成"),
    DELIVERY_SUBMITTED("交付物提交"),
    DELIVERY_REJECTED("交付物退回"),
    REVIEW_REQUEST("评价邀请"),
    REPORT_RESULT("举报结果"),
    APPEAL_RESULT("申诉结果"),
    SYSTEM_BROADCAST("系统公告"),
    SYSTEM_NOTICE("系统通知"),
    TASK_UPDATE("任务更新"),
    CREDIT_CHANGE("信用分变更"),
    FREEZE_NOTICE("账户冻结通知"),
    REMINDER("任务提醒"),
    NICKNAME_APPROVED("昵称审核通过"),
    NICKNAME_REJECTED("昵称审核拒绝"),
    AVATAR_APPROVED("头像审核通过"),
    AVATAR_REJECTED("头像审核拒绝"),
    ANNOUNCEMENT_APPROVED("公告审核通过"),
    ANNOUNCEMENT_REJECTED("公告审核拒绝"),
    OVERDUE_WARNING("超时提醒");

    private final String displayName;
    NotificationType(String displayName) { this.displayName = displayName; }
    public String getDisplayName() { return displayName; }
}
