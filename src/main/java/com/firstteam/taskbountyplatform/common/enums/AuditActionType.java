package com.firstteam.taskbountyplatform.common.enums;

public enum AuditActionType {
    // 用户操作
    USER_REGISTER("用户注册"),
    USER_LOGIN("用户登录"),
    TASK_PUBLISH("发布任务"),
    TASK_EDIT("编辑任务"),
    TASK_CANCEL("取消任务"),
    TASK_AWARD("选择中标者"),
    TASK_APPLY("申请接单"),
    TASK_ABANDON("放弃任务"),
    DELIVERY_SUBMIT("提交交付物"),
    DELIVERY_CONFIRM("确认完成"),
    DELIVERY_REJECT("退回修改"),
    REVIEW_SUBMIT("提交评价"),
    REPORT_SUBMIT("提交举报"),
    NICKNAME_CHANGE("修改昵称"),
    AVATAR_CHANGE("修改头像"),
    ANNOUNCEMENT_CHANGE("修改公告栏"),
    APPEAL_SUBMIT("提交申诉"),
    // 管理员操作
    ADMIN_FREEZE_USER("冻结用户"),
    ADMIN_UNFREEZE_USER("解冻用户"),
    ADMIN_APPROVE_REPORT("核实举报"),
    ADMIN_REJECT_REPORT("驳回举报"),
    ADMIN_FORCE_CANCEL_TASK("强制下架任务"),
    ADMIN_RESOLVE_APPEAL("处理申诉"),
    ADMIN_APPROVE_NICKNAME("通过昵称审核"),
    ADMIN_REJECT_NICKNAME("拒绝昵称审核"),
    ADMIN_APPROVE_AVATAR("通过头像审核"),
    ADMIN_REJECT_AVATAR("拒绝头像审核"),
    ADMIN_APPROVE_ANNOUNCEMENT("通过公告审核"),
    ADMIN_REJECT_ANNOUNCEMENT("拒绝公告审核"),
    ADMIN_UPDATE_CONFIG("修改系统配置"),
    ADMIN_CREDIT_RESET("重置信用分"),
    ADMIN_BROADCAST("发送系统公告"),
    ADMIN_MIGRATE_CATEGORY("迁移任务分类"),
    // 系统操作
    SYSTEM_AUTO_CANCEL("系统自动取消"),
    SYSTEM_AUTO_COMPLETE("系统自动完成"),
    SYSTEM_AUTO_REVIEW("系统默认评价"),
    SYSTEM_FREEZE_GRADUATED("毕业生账户冻结"),
    SYSTEM_INFO_SYNC("信息同步"),
    SYSTEM_FILE_CLEANUP("附件清理");

    private final String displayName;
    AuditActionType(String displayName) { this.displayName = displayName; }
    public String getDisplayName() { return displayName; }
}
