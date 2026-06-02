package com.firstteam.taskbountyplatform.user.dto;

import jakarta.validation.constraints.Size;

public class UpdateAnnouncementRequest {

    @Size(max = 200)
    private String announcement;

    public String getAnnouncement() {
        return announcement;
    }

    public void setAnnouncement(String announcement) {
        this.announcement = announcement;
    }
}
