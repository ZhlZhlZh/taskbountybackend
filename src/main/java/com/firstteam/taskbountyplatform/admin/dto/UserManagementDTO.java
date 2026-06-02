package com.firstteam.taskbountyplatform.admin.dto;

import java.time.LocalDateTime;

public class UserManagementDTO {
    private Long id;
    private String studentNo;
    private String nickname;
    private String realName;
    private Integer creditScore;
    private String accountStatus;
    private String role;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginTime;
    private Integer graduationFreezeCount;
    private Boolean creditResetUsed;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getStudentNo() {
        return studentNo;
    }

    public void setStudentNo(String studentNo) {
        this.studentNo = studentNo;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public Integer getCreditScore() {
        return creditScore;
    }

    public void setCreditScore(Integer creditScore) {
        this.creditScore = creditScore;
    }

    public String getAccountStatus() {
        return accountStatus;
    }

    public void setAccountStatus(String accountStatus) {
        this.accountStatus = accountStatus;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getLastLoginTime() {
        return lastLoginTime;
    }

    public void setLastLoginTime(LocalDateTime lastLoginTime) {
        this.lastLoginTime = lastLoginTime;
    }

    public Integer getGraduationFreezeCount() {
        return graduationFreezeCount;
    }

    public void setGraduationFreezeCount(Integer graduationFreezeCount) {
        this.graduationFreezeCount = graduationFreezeCount;
    }

    public Boolean getCreditResetUsed() {
        return creditResetUsed;
    }

    public void setCreditResetUsed(Boolean creditResetUsed) {
        this.creditResetUsed = creditResetUsed;
    }
}
