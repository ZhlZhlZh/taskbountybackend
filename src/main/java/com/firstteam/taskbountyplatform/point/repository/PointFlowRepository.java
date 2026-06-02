package com.firstteam.taskbountyplatform.point.repository;

import com.firstteam.taskbountyplatform.point.entity.PointFlow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface PointFlowRepository extends JpaRepository<PointFlow, Long> {
    Page<PointFlow> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<PointFlow> findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long userId, LocalDateTime start, LocalDateTime end, Pageable pageable);

    Page<PointFlow> findByUserIdAndFlowTypeOrderByCreatedAtDesc(
            Long userId, String flowType, Pageable pageable);
}
