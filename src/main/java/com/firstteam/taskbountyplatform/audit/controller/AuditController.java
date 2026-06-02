package com.firstteam.taskbountyplatform.audit.controller;

import com.firstteam.taskbountyplatform.audit.entity.AuditLog;
import com.firstteam.taskbountyplatform.audit.service.AuditLogService;
import com.firstteam.taskbountyplatform.common.response.ApiResponse;
import com.firstteam.taskbountyplatform.common.response.PageResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * AuditController - admin-only REST API for audit log access.
 */
@RestController
@RequestMapping("/api/admin/audit-logs")
@PreAuthorize("hasAuthority('ADMIN')")
public class AuditController {

    private final AuditLogService auditLogService;

    public AuditController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    /**
     * GET /api/admin/audit-logs - get paginated audit logs with optional operatorId filter.
     */
    @GetMapping
    public ApiResponse<PageResult<AuditLog>> getAuditLogs(
            @RequestParam(required = false) Long operatorId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int size) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AuditLog> result = auditLogService.getAuditLogs(operatorId, pageable);
        PageResult<AuditLog> pageResult = new PageResult<>(
                result.getContent(), page, size, result.getTotalElements());
        return ApiResponse.success(pageResult);
    }
}
