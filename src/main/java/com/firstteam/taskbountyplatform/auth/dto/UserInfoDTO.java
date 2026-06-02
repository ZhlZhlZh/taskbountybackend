package com.firstteam.taskbountyplatform.auth.dto;

import java.time.LocalDateTime;

public class UserInfoDTO {
    private Long id;
    private String studentNo;
    private String realName;
    private String nickname;
    private String avatarUrl;
    private String announcement;
    private String grade;
    private String college;
    private String academy;
    private Integer creditScore;
    private String accountStatus;
    private String role;
    private Integer availablePoints;
    private Integer frozenPoints;
    private Integer totalIncome;
    private Integer totalExpense;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public String getStudentNo() { return studentNo; }
    public void setStudentNo(String v) { this.studentNo = v; }
    public String getRealName() { return realName; }
    public void setRealName(String v) { this.realName = v; }
    public String getNickname() { return nickname; }
    public void setNickname(String v) { this.nickname = v; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String v) { this.avatarUrl = v; }
    public String getAnnouncement() { return announcement; }
    public void setAnnouncement(String v) { this.announcement = v; }
    public String getGrade() { return grade; }
    public void setGrade(String v) { this.grade = v; }
    public String getCollege() { return college; }
    public void setCollege(String v) { this.college = v; }
    public String getAcademy() { return academy; }
    public void setAcademy(String v) { this.academy = v; }
    public Integer getCreditScore() { return creditScore; }
    public void setCreditScore(Integer v) { this.creditScore = v; }
    public String getAccountStatus() { return accountStatus; }
    public void setAccountStatus(String v) { this.accountStatus = v; }
    public String getRole() { return role; }
    public void setRole(String v) { this.role = v; }
    public Integer getAvailablePoints() { return availablePoints; }
    public void setAvailablePoints(Integer v) { this.availablePoints = v; }
    public Integer getFrozenPoints() { return frozenPoints; }
    public void setFrozenPoints(Integer v) { this.frozenPoints = v; }
    public Integer getTotalIncome() { return totalIncome; }
    public void setTotalIncome(Integer v) { this.totalIncome = v; }
    public Integer getTotalExpense() { return totalExpense; }
    public void setTotalExpense(Integer v) { this.totalExpense = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
}
