package com.firstteam.taskbountyplatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "file.upload")
public class FileUploadConfig {
    private String baseDir = "./uploads";
    private long avatarMaxSize = 1048576;
    private int avatarWidth = 225;
    private int avatarHeight = 225;
    private long attachmentMaxSize = 20971520;
    private long deliveryMaxSize = 31457280;
    private int maxFilesPerTask = 3;

    public String getBaseDir() { return baseDir; }
    public void setBaseDir(String baseDir) { this.baseDir = baseDir; }
    public long getAvatarMaxSize() { return avatarMaxSize; }
    public void setAvatarMaxSize(long avatarMaxSize) { this.avatarMaxSize = avatarMaxSize; }
    public int getAvatarWidth() { return avatarWidth; }
    public void setAvatarWidth(int avatarWidth) { this.avatarWidth = avatarWidth; }
    public int getAvatarHeight() { return avatarHeight; }
    public void setAvatarHeight(int avatarHeight) { this.avatarHeight = avatarHeight; }
    public long getAttachmentMaxSize() { return attachmentMaxSize; }
    public void setAttachmentMaxSize(long attachmentMaxSize) { this.attachmentMaxSize = attachmentMaxSize; }
    public long getDeliveryMaxSize() { return deliveryMaxSize; }
    public void setDeliveryMaxSize(long deliveryMaxSize) { this.deliveryMaxSize = deliveryMaxSize; }
    public int getMaxFilesPerTask() { return maxFilesPerTask; }
    public void setMaxFilesPerTask(int maxFilesPerTask) { this.maxFilesPerTask = maxFilesPerTask; }
}
