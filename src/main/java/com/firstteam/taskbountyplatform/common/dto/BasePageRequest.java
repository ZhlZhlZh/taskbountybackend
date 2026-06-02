package com.firstteam.taskbountyplatform.common.dto;

import jakarta.validation.constraints.Min;

public class BasePageRequest {
    @Min(1)
    private int page = 1;
    @Min(1)
    private int size = 15;

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
}
