package com.firstteam.taskbountyplatform.delivery.dto;

import com.firstteam.taskbountyplatform.file.dto.FileDTO;

import java.time.LocalDateTime;
import java.util.List;

public class DeliveryDTO {
    private Long id;
    private Long taskId;
    private Long workerId;
    private String workerNickname;
    private String description;
    private String status;
    private LocalDateTime submitTime;
    private Integer rejectCount;
    private List<FileDTO> files;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public Long getWorkerId() {
        return workerId;
    }

    public void setWorkerId(Long workerId) {
        this.workerId = workerId;
    }

    public String getWorkerNickname() {
        return workerNickname;
    }

    public void setWorkerNickname(String workerNickname) {
        this.workerNickname = workerNickname;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getSubmitTime() {
        return submitTime;
    }

    public void setSubmitTime(LocalDateTime submitTime) {
        this.submitTime = submitTime;
    }

    public Integer getRejectCount() {
        return rejectCount;
    }

    public void setRejectCount(Integer rejectCount) {
        this.rejectCount = rejectCount;
    }

    public List<FileDTO> getFiles() {
        return files;
    }

    public void setFiles(List<FileDTO> files) {
        this.files = files;
    }
}
