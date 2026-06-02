package com.firstteam.taskbountyplatform.common.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class BatchOperationRequest {

    @NotEmpty
    private List<Long> ids;

    private String action;

    public List<Long> getIds() {
        return ids;
    }

    public void setIds(List<Long> ids) {
        this.ids = ids;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }
}
