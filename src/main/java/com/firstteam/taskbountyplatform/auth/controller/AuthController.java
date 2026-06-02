package com.firstteam.taskbountyplatform.auth.controller;

import com.firstteam.taskbountyplatform.auth.dto.LoginRequest;
import com.firstteam.taskbountyplatform.auth.dto.LoginResponse;
import com.firstteam.taskbountyplatform.auth.service.AuthService;
import com.firstteam.taskbountyplatform.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @GetMapping("/me")
    public ApiResponse<?> getCurrentUser() {
        return ApiResponse.success(authService.getCurrentUser());
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        return ApiResponse.success("已退出登录", null);
    }
}
