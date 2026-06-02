package com.firstteam.taskbountyplatform.auth.dto;

public class LoginResponse {
    private String token;
    private UserInfoDTO user;

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public UserInfoDTO getUser() { return user; }
    public void setUser(UserInfoDTO user) { this.user = user; }
}
