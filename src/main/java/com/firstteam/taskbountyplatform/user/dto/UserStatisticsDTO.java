package com.firstteam.taskbountyplatform.user.dto;

public class UserStatisticsDTO {
    private int publishedTaskCount;
    private int completedTaskCount;
    private int cancelledTaskCount;
    private String completionRate;

    public int getPublishedTaskCount() {
        return publishedTaskCount;
    }

    public void setPublishedTaskCount(int publishedTaskCount) {
        this.publishedTaskCount = publishedTaskCount;
    }

    public int getCompletedTaskCount() {
        return completedTaskCount;
    }

    public void setCompletedTaskCount(int completedTaskCount) {
        this.completedTaskCount = completedTaskCount;
    }

    public int getCancelledTaskCount() {
        return cancelledTaskCount;
    }

    public void setCancelledTaskCount(int cancelledTaskCount) {
        this.cancelledTaskCount = cancelledTaskCount;
    }

    public String getCompletionRate() {
        return completionRate;
    }

    public void setCompletionRate(String completionRate) {
        this.completionRate = completionRate;
    }
}
