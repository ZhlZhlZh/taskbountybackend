package com.firstteam.taskbountyplatform.common.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class IdListRequest {

    @NotEmpty
    private List<Long> ids;

    public List<Long> getIds() {
        return ids;
    }

    public void setIds(List<Long> ids) {
        this.ids = ids;
    }
}
