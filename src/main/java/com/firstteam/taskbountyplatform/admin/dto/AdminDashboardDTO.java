package com.firstteam.taskbountyplatform.admin.dto;

public class AdminDashboardDTO {
    private long totalUsers;
    private long newUsersToday;
    private int onlineUsers;
    private long totalTasks;
    private long inProgressTasks;
    private long pendingConfirmTasks;
    private long overdueTasks;
    private long pendingAvatarAudits;
    private long pendingNicknameAudits;
    private long pendingAnnouncementAudits;
    private long pendingAppeals;
    private long pendingReports;
    private int platformBalance;
    private int weeklyFee;

    public long getTotalUsers() {
        return totalUsers;
    }

    public void setTotalUsers(long totalUsers) {
        this.totalUsers = totalUsers;
    }

    public long getNewUsersToday() {
        return newUsersToday;
    }

    public void setNewUsersToday(long newUsersToday) {
        this.newUsersToday = newUsersToday;
    }

    public int getOnlineUsers() {
        return onlineUsers;
    }

    public void setOnlineUsers(int onlineUsers) {
        this.onlineUsers = onlineUsers;
    }

    public long getTotalTasks() {
        return totalTasks;
    }

    public void setTotalTasks(long totalTasks) {
        this.totalTasks = totalTasks;
    }

    public long getInProgressTasks() {
        return inProgressTasks;
    }

    public void setInProgressTasks(long inProgressTasks) {
        this.inProgressTasks = inProgressTasks;
    }

    public long getPendingConfirmTasks() {
        return pendingConfirmTasks;
    }

    public void setPendingConfirmTasks(long pendingConfirmTasks) {
        this.pendingConfirmTasks = pendingConfirmTasks;
    }

    public long getOverdueTasks() {
        return overdueTasks;
    }

    public void setOverdueTasks(long overdueTasks) {
        this.overdueTasks = overdueTasks;
    }

    public long getPendingAvatarAudits() {
        return pendingAvatarAudits;
    }

    public void setPendingAvatarAudits(long pendingAvatarAudits) {
        this.pendingAvatarAudits = pendingAvatarAudits;
    }

    public long getPendingNicknameAudits() {
        return pendingNicknameAudits;
    }

    public void setPendingNicknameAudits(long pendingNicknameAudits) {
        this.pendingNicknameAudits = pendingNicknameAudits;
    }

    public long getPendingAnnouncementAudits() {
        return pendingAnnouncementAudits;
    }

    public void setPendingAnnouncementAudits(long pendingAnnouncementAudits) {
        this.pendingAnnouncementAudits = pendingAnnouncementAudits;
    }

    public long getPendingAppeals() {
        return pendingAppeals;
    }

    public void setPendingAppeals(long pendingAppeals) {
        this.pendingAppeals = pendingAppeals;
    }

    public long getPendingReports() {
        return pendingReports;
    }

    public void setPendingReports(long pendingReports) {
        this.pendingReports = pendingReports;
    }

    public int getPlatformBalance() {
        return platformBalance;
    }

    public void setPlatformBalance(int platformBalance) {
        this.platformBalance = platformBalance;
    }

    public int getWeeklyFee() {
        return weeklyFee;
    }

    public void setWeeklyFee(int weeklyFee) {
        this.weeklyFee = weeklyFee;
    }
}
