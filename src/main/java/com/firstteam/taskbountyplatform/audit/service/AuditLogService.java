package com.firstteam.taskbountyplatform.audit.service;

import com.firstteam.taskbountyplatform.common.enums.AuditActionType;
import com.firstteam.taskbountyplatform.audit.entity.AuditLog;
import com.firstteam.taskbountyplatform.audit.repository.AuditLogRepository;
import com.firstteam.taskbountyplatform.auth.security.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

/**
 * AuditLogService - simple logging service for recording system operations.
 * Called by other services to record audit trail entries.
 */
@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository auditLogRepository;
    private final UserContext userContext;

    public AuditLogService(AuditLogRepository auditLogRepository,
                            UserContext userContext) {
        this.auditLogRepository = auditLogRepository;
        this.userContext = userContext;
    }

    /**
     * Full log method with all parameters specified.
     */
    @Transactional
    public AuditLog log(Long operatorId, AuditActionType actionType, String targetType,
                         Long targetId, String detail, String ip) {
        AuditLog auditLog = new AuditLog();
        auditLog.setOperatorId(operatorId);
        auditLog.setActionType(actionType);
        auditLog.setTargetType(targetType);
        auditLog.setTargetId(targetId);
        auditLog.setDetail(detail);
        auditLog.setIp(ip != null ? ip : "127.0.0.1");
        auditLog.setCreatedAt(LocalDateTime.now());

        AuditLog saved = auditLogRepository.save(auditLog);
        log.debug("Audit log created: id={}, operatorId={}, actionType={}, targetType={}, targetId={}",
                saved.getId(), operatorId, actionType, targetType, targetId);
        return saved;
    }

    /**
     * Simplified log method - derives operatorId from context, IP from request.
     * Uses null for system operations when no authenticated user.
     */
    @Transactional
    public AuditLog log(AuditActionType actionType, String targetType, Long targetId, String detail) {
        Long operatorId;
        try {
            operatorId = userContext.getCurrentUserId();
        } catch (RuntimeException e) {
            // No authenticated user - this is a system operation
            operatorId = null;
        }

        String ip = getClientIp();
        return log(operatorId, actionType, targetType, targetId, detail, ip);
    }

    /**
     * Get paginated audit logs, optionally filtered by operatorId.
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> getAuditLogs(Long operatorId, Pageable pageable) {
        if (operatorId != null) {
            return auditLogRepository.findByOperatorIdOrderByCreatedAtDesc(operatorId, pageable);
        }
        return auditLogRepository.findAll(pageable);
    }

    /**
     * Extract client IP from the current HTTP request.
     */
    private String getClientIp() {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String ip = request.getHeader("X-Forwarded-For");
                if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getHeader("X-Real-IP");
                }
                if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getRemoteAddr();
                }
                return ip;
            }
        } catch (Exception e) {
            log.debug("Could not extract client IP", e);
        }
        return "127.0.0.1";
    }
}
