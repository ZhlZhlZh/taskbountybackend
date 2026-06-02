package com.firstteam.taskbountyplatform.report.controller;

import com.firstteam.taskbountyplatform.auth.security.UserContext;
import com.firstteam.taskbountyplatform.common.response.ApiResponse;
import com.firstteam.taskbountyplatform.common.response.PageResult;
import com.firstteam.taskbountyplatform.report.dto.ReportProcessRequest;
import com.firstteam.taskbountyplatform.report.dto.ReportSubmitRequest;
import com.firstteam.taskbountyplatform.report.entity.Report;
import com.firstteam.taskbountyplatform.report.service.ReportService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class ReportController {

    private final ReportService reportService;
    private final UserContext userContext;

    public ReportController(ReportService reportService, UserContext userContext) {
        this.reportService = reportService;
        this.userContext = userContext;
    }

    /**
     * POST /api/reports - Submit a report.
     */
    @PostMapping("/reports")
    public ApiResponse<Report> submitReport(@Valid @RequestBody ReportSubmitRequest request) {
        Long userId = userContext.getCurrentUserId();
        Report report = reportService.submitReport(request, userId);
        return ApiResponse.success("举报提交成功", report);
    }

    /**
     * GET /api/reports - Get reports (own for user, all for admin).
     */
    @GetMapping("/reports")
    public ApiResponse<PageResult<Report>> getReports(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int size) {
        Long userId = userContext.getCurrentUserId();
        boolean isAdmin = userContext.isAdmin();
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by("createdAt").descending());
        Page<Report> reportPage = reportService.getReports(null, pageable, isAdmin);

        // If not admin, filter by reporterId in-memory
        if (!isAdmin) {
            java.util.List<Report> filtered = reportPage.getContent().stream()
                    .filter(r -> r.getReporterId().equals(userId))
                    .toList();
            reportPage = new PageImpl<>(filtered, pageable, filtered.size());
        }

        PageResult<Report> result = new PageResult<>(
                reportPage.getContent(), page, size, reportPage.getTotalElements());
        return ApiResponse.success(result);
    }

    /**
     * GET /api/reports/{reportId} - Get report detail.
     */
    @GetMapping("/reports/{reportId}")
    public ApiResponse<Report> getReportDetail(@PathVariable Long reportId) {
        Report report = reportService.getReportDetail(reportId);
        return ApiResponse.success(report);
    }

    /**
     * GET /api/admin/reports - Admin list all reports (with optional ?status= filter).
     */
    @GetMapping("/admin/reports")
    public ApiResponse<PageResult<Report>> adminGetReports(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int size) {
        if (!userContext.isAdmin()) {
            return ApiResponse.error(403, "无权限访问");
        }
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by("createdAt").descending());
        Page<Report> reportPage = reportService.getReports(status, pageable, true);
        PageResult<Report> result = new PageResult<>(
                reportPage.getContent(), page, size, reportPage.getTotalElements());
        return ApiResponse.success(result);
    }

    /**
     * POST /api/admin/reports/{reportId}/approve - Approve report (admin).
     */
    @PostMapping("/admin/reports/{reportId}/approve")
    public ApiResponse<String> approveReport(@PathVariable Long reportId,
                                             @RequestBody ReportProcessRequest request) {
        if (!userContext.isAdmin()) {
            return ApiResponse.error(403, "无权限访问");
        }
        Long adminId = userContext.getCurrentUserId();
        reportService.approveReport(reportId, adminId, request);
        return ApiResponse.success("举报已核实通过，已执行处罚");
    }

    /**
     * POST /api/admin/reports/{reportId}/reject - Reject report (admin).
     */
    @PostMapping("/admin/reports/{reportId}/reject")
    public ApiResponse<String> rejectReport(@PathVariable Long reportId,
                                            @RequestBody Map<String, String> body) {
        if (!userContext.isAdmin()) {
            return ApiResponse.error(403, "无权限访问");
        }
        Long adminId = userContext.getCurrentUserId();
        String reason = body.getOrDefault("reason", "无说明");
        reportService.rejectReport(reportId, adminId, reason);
        return ApiResponse.success("举报已驳回");
    }

    /**
     * POST /api/admin/reports/{reportId}/reject-with-penalty - Reject with penalty for false reports.
     */
    @PostMapping("/admin/reports/{reportId}/reject-with-penalty")
    public ApiResponse<String> rejectReportWithPenalty(@PathVariable Long reportId,
                                                        @RequestBody Map<String, String> body) {
        if (!userContext.isAdmin()) {
            return ApiResponse.error(403, "无权限访问");
        }
        Long adminId = userContext.getCurrentUserId();
        String reason = body.getOrDefault("reason", "无说明");
        reportService.rejectReportWithPenalty(reportId, adminId, reason);
        return ApiResponse.success("举报已驳回，举报者已扣除3分信用分");
    }
}
