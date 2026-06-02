package com.firstteam.taskbountyplatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "platform")
public class PlatformConfig {
    private Credit credit = new Credit();
    private Task task = new Task();
    private Attachment attachment = new Attachment();
    private int initialPoints = 1000;
    private int nicknameModifyCooldownDays = 30;
    private int freezeDurationDays = 40;
    private int maxConcurrentOrders = 3;
    private String userSyncCron = "0 0 3 1 * ?";

    public static class Credit {
        private int initialScore = 80;
        private int minScore = 0;
        private int maxScore = 100;
        private int warningThreshold = 60;
        private int restrictThreshold = 40;
        private int completionRateGood = 90;
        private int completionRateBad = 70;
        private int completionGoodScore = 5;
        private int completionBadScore = -5;
        private int praiseRateGood = 95;
        private int praiseRateBad = 80;
        private int praiseGoodScore = 3;
        private int praiseBadScore = -3;
        private int overtimePenalty = -8;
        private int voluntaryQuitPenalty = -3;
        private int publisherCancelPenalty = -5;

        public int getInitialScore() { return initialScore; }
        public void setInitialScore(int v) { this.initialScore = v; }
        public int getMinScore() { return minScore; }
        public void setMinScore(int v) { this.minScore = v; }
        public int getMaxScore() { return maxScore; }
        public void setMaxScore(int v) { this.maxScore = v; }
        public int getWarningThreshold() { return warningThreshold; }
        public void setWarningThreshold(int v) { this.warningThreshold = v; }
        public int getRestrictThreshold() { return restrictThreshold; }
        public void setRestrictThreshold(int v) { this.restrictThreshold = v; }
        public int getCompletionRateGood() { return completionRateGood; }
        public void setCompletionRateGood(int v) { this.completionRateGood = v; }
        public int getCompletionRateBad() { return completionRateBad; }
        public void setCompletionRateBad(int v) { this.completionRateBad = v; }
        public int getCompletionGoodScore() { return completionGoodScore; }
        public void setCompletionGoodScore(int v) { this.completionGoodScore = v; }
        public int getCompletionBadScore() { return completionBadScore; }
        public void setCompletionBadScore(int v) { this.completionBadScore = v; }
        public int getPraiseRateGood() { return praiseRateGood; }
        public void setPraiseRateGood(int v) { this.praiseRateGood = v; }
        public int getPraiseRateBad() { return praiseRateBad; }
        public void setPraiseRateBad(int v) { this.praiseRateBad = v; }
        public int getPraiseGoodScore() { return praiseGoodScore; }
        public void setPraiseGoodScore(int v) { this.praiseGoodScore = v; }
        public int getPraiseBadScore() { return praiseBadScore; }
        public void setPraiseBadScore(int v) { this.praiseBadScore = v; }
        public int getOvertimePenalty() { return overtimePenalty; }
        public void setOvertimePenalty(int v) { this.overtimePenalty = v; }
        public int getVoluntaryQuitPenalty() { return voluntaryQuitPenalty; }
        public void setVoluntaryQuitPenalty(int v) { this.voluntaryQuitPenalty = v; }
        public int getPublisherCancelPenalty() { return publisherCancelPenalty; }
        public void setPublisherCancelPenalty(int v) { this.publisherCancelPenalty = v; }
    }

    public static class Task {
        private int autoCancelDays = 14;
        private int reminderHours = 24;
        private int extendRatio = 50;
        private int maxExtendTimes = 2;
        private int autoConfirmHours = 72;

        public int getAutoCancelDays() { return autoCancelDays; }
        public void setAutoCancelDays(int v) { this.autoCancelDays = v; }
        public int getReminderHours() { return reminderHours; }
        public void setReminderHours(int v) { this.reminderHours = v; }
        public int getExtendRatio() { return extendRatio; }
        public void setExtendRatio(int v) { this.extendRatio = v; }
        public int getMaxExtendTimes() { return maxExtendTimes; }
        public void setMaxExtendTimes(int v) { this.maxExtendTimes = v; }
        public int getAutoConfirmHours() { return autoConfirmHours; }
        public void setAutoConfirmHours(int v) { this.autoConfirmHours = v; }
    }

    public static class Attachment {
        private int saveDays = 30;
        private long maxFileSize = 20971520;
        private long maxDeliverySize = 31457280;

        public int getSaveDays() { return saveDays; }
        public void setSaveDays(int v) { this.saveDays = v; }
        public long getMaxFileSize() { return maxFileSize; }
        public void setMaxFileSize(long v) { this.maxFileSize = v; }
        public long getMaxDeliverySize() { return maxDeliverySize; }
        public void setMaxDeliverySize(long v) { this.maxDeliverySize = v; }
    }

    public Credit getCredit() { return credit; }
    public void setCredit(Credit c) { this.credit = c; }
    public Task getTask() { return task; }
    public void setTask(Task t) { this.task = t; }
    public Attachment getAttachment() { return attachment; }
    public void setAttachment(Attachment a) { this.attachment = a; }
    public int getInitialPoints() { return initialPoints; }
    public void setInitialPoints(int v) { this.initialPoints = v; }
    public int getNicknameModifyCooldownDays() { return nicknameModifyCooldownDays; }
    public void setNicknameModifyCooldownDays(int v) { this.nicknameModifyCooldownDays = v; }
    public int getFreezeDurationDays() { return freezeDurationDays; }
    public void setFreezeDurationDays(int v) { this.freezeDurationDays = v; }
    public int getMaxConcurrentOrders() { return maxConcurrentOrders; }
    public void setMaxConcurrentOrders(int v) { this.maxConcurrentOrders = v; }
    public String getUserSyncCron() { return userSyncCron; }
    public void setUserSyncCron(String v) { this.userSyncCron = v; }
}
