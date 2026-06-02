package com.firstteam.taskbountyplatform.auth.dto;

import jakarta.validation.constraints.NotBlank;

public class LoginRequest {
    @NotBlank(message = "学号/工号不能为空")
    private String studentNo;
    private String password; // optional for simulated OAuth

    public String getStudentNo() { return studentNo; }
    public void setStudentNo(String studentNo) { this.studentNo = studentNo; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
