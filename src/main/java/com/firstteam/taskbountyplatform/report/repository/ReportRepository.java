package com.firstteam.taskbountyplatform.report.repository;

import com.firstteam.taskbountyplatform.common.enums.ReportStatus;
import com.firstteam.taskbountyplatform.report.entity.Report;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {
    Page<Report> findByStatus(ReportStatus status, Pageable pageable);
    List<Report> findByTargetTypeAndTargetId(String targetType, Long targetId);
    long countByStatus(ReportStatus status);
}
