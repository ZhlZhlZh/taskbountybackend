package com.firstteam.taskbountyplatform.admin.dto;

import jakarta.validation.constraints.NotBlank;

public class SystemConfigUpdateRequest {

    @NotBlank
    private String configValue;

    public String getConfigValue() {
        return configValue;
    }

    public void setConfigValue(String configValue) {
        this.configValue = configValue;
    }
}
