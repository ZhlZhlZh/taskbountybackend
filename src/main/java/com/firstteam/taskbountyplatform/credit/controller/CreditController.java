package com.firstteam.taskbountyplatform.credit.controller;

import com.firstteam.taskbountyplatform.auth.security.UserContext;
import com.firstteam.taskbountyplatform.common.response.ApiResponse;
import com.firstteam.taskbountyplatform.credit.dto.CreditRuleUpdateRequest;
import com.firstteam.taskbountyplatform.credit.entity.CreditRuleConfig;
import com.firstteam.taskbountyplatform.credit.service.CreditService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 信用分管理控制器 — 仅管理员端点。
 * 用户个人信用分明细查询在 UserController 中（GET /api/users/me/credit-records）。
 */
@RestController
@RequestMapping("/api")
public class CreditController {

    private final CreditService creditService;
    private final UserContext userContext;

    public CreditController(CreditService creditService, UserContext userContext) {
        this.creditService = creditService;
        this.userContext = userContext;
    }

    /**
     * GET /api/admin/credit-rules - 获取全部信用分规则配置（管理员）。
     */
    @GetMapping("/admin/credit-rules")
    public ApiResponse<List<CreditRuleConfig>> getCreditRules() {
        if (!userContext.isAdmin()) {
            return ApiResponse.error(403, "无权限访问");
        }
        return ApiResponse.success(creditService.getCreditRules());
    }

    /**
     * PUT /api/admin/credit-rules - 更新信用分规则配置（管理员）。
     */
    @PutMapping("/admin/credit-rules")
    public ApiResponse<String> updateCreditRules(
            @Valid @RequestBody List<CreditRuleUpdateRequest> rules) {
        if (!userContext.isAdmin()) {
            return ApiResponse.error(403, "无权限访问");
        }
        creditService.updateCreditRules(rules);
        return ApiResponse.success("信用规则更新成功");
    }
}
