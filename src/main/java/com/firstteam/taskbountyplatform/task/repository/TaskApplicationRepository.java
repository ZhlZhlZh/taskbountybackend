package com.firstteam.taskbountyplatform.task.repository;

import com.firstteam.taskbountyplatform.common.enums.ApplicationStatus;
import com.firstteam.taskbountyplatform.task.entity.TaskApplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaskApplicationRepository extends JpaRepository<TaskApplication, Long>, JpaSpecificationExecutor<TaskApplication> {
    List<TaskApplication> findByTaskIdOrderByApplicantIdAsc(Long taskId);

    List<TaskApplication> findByTaskIdAndStatus(Long taskId, ApplicationStatus status);

    Optional<TaskApplication> findByTaskIdAndApplicantId(Long taskId, Long applicantId);

    boolean existsByTaskIdAndApplicantId(Long taskId, Long applicantId);

    Page<TaskApplication> findByApplicantIdOrderByAppliedAtDesc(Long applicantId, Pageable pageable);

    Page<TaskApplication> findByApplicantIdAndStatus(Long applicantId, ApplicationStatus status, Pageable pageable);

    boolean existsByTaskIdAndStatus(Long taskId, ApplicationStatus status);

    // 接单者当前进行中的申请数（已中标但任务未完成）
    @Query("SELECT COUNT(ta) FROM TaskApplication ta WHERE ta.applicantId = :applicantId AND ta.status = 'AWARDED'")
    long countActiveOrders(@Param("applicantId") Long applicantId);

    List<TaskApplication> findByApplicantIdAndStatus(Long applicantId, ApplicationStatus status);

    // 最近7天申请数排名
    @Query("SELECT ta.taskId, COUNT(ta) FROM TaskApplication ta GROUP BY ta.taskId ORDER BY COUNT(ta) DESC")
    List<Object[]> findTopTaskIdsByApplications(Pageable pageable);
}
