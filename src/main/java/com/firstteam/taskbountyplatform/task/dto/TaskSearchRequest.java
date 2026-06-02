package com.firstteam.taskbountyplatform.task.dto;

import java.time.LocalDate;
import java.util.List;

public class TaskSearchRequest {
    private String keyword;
    private List<Long> categoryIds;
    private Integer minReward;
    private Integer maxReward;
    private LocalDate startDate;
    private LocalDate endDate;
    private String sortBy;
    private int page = 1;
    private int size = 12;

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public List<Long> getCategoryIds() {
        return categoryIds;
    }

    public void setCategoryIds(List<Long> categoryIds) {
        this.categoryIds = categoryIds;
    }

    public Integer getMinReward() {
        return minReward;
    }

    public void setMinReward(Integer minReward) {
        this.minReward = minReward;
    }

    public Integer getMaxReward() {
        return maxReward;
    }

    public void setMaxReward(Integer maxReward) {
        this.maxReward = maxReward;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public String getSortBy() {
        return sortBy;
    }

    public void setSortBy(String sortBy) {
        this.sortBy = sortBy;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }
}
