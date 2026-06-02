package com.firstteam.taskbountyplatform.admin.repository;

import com.firstteam.taskbountyplatform.admin.entity.ReviewAudit;
import com.firstteam.taskbountyplatform.common.enums.ReviewAuditStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewAuditRepository extends JpaRepository<ReviewAudit, Long> {
    Page<ReviewAudit> findByStatus(ReviewAuditStatus status, Pageable pageable);
    Page<ReviewAudit> findByAuditType(String auditType, Pageable pageable);
    List<ReviewAudit> findByApplicantIdAndAuditTypeAndStatus(Long applicantId, String auditType, ReviewAuditStatus status);
    long countByStatus(ReviewAuditStatus status);
}
