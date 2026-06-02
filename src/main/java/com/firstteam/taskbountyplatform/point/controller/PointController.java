package com.firstteam.taskbountyplatform.point.controller;

import com.firstteam.taskbountyplatform.auth.security.UserContext;
import com.firstteam.taskbountyplatform.common.response.ApiResponse;
import com.firstteam.taskbountyplatform.common.response.PageResult;
import com.firstteam.taskbountyplatform.point.entity.PointAccount;
import com.firstteam.taskbountyplatform.point.entity.PointFlow;
import com.firstteam.taskbountyplatform.point.service.PointService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/points")
public class PointController {

    private final PointService pointService;
    private final UserContext userContext;

    public PointController(PointService pointService, UserContext userContext) {
        this.pointService = pointService;
        this.userContext = userContext;
    }

    /**
     * Get the current user's point account.
     */
    @GetMapping("/account")
    public ApiResponse<PointAccount> getAccount() {
        Long currentUserId = userContext.getCurrentUserId();
        PointAccount account = pointService.getAccount(currentUserId);
        return ApiResponse.success(account);
    }

    /**
     * Get point flow history for the current user with optional date range and type filtering.
     */
    @GetMapping("/flows")
    public ApiResponse<PageResult<PointFlow>> getPointFlows(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(required = false) String flowType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int size) {
        Long currentUserId = userContext.getCurrentUserId();
        Pageable pageable = PageRequest.of(page - 1, size);
        Page<PointFlow> flowsPage = pointService.getPointFlows(currentUserId, start, end, flowType, pageable);

        PageResult<PointFlow> result = new PageResult<>(
                flowsPage.getContent(),
                page,
                size,
                flowsPage.getTotalElements()
        );
        return ApiResponse.success(result);
    }

    /**
     * Get the platform's total point balance (admin or public).
     */
    @GetMapping("/platform-balance")
    public ApiResponse<Map<String, Object>> getPlatformBalance() {
        long balance = pointService.getPlatformBalance();
        Map<String, Object> result = new HashMap<>();
        result.put("platformBalance", balance);
        return ApiResponse.success(result);
    }
}
