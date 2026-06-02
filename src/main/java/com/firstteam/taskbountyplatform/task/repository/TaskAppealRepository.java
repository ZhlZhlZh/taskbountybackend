package com.firstteam.taskbountyplatform.task.repository;

import com.firstteam.taskbountyplatform.task.entity.TaskAppeal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaskAppealRepository extends JpaRepository<TaskAppeal, Long> {
    Optional<TaskAppeal> findByTaskIdAndStatus(Long taskId, String status);
    List<TaskAppeal> findByStatus(String status);
    Page<TaskAppeal> findByStatus(String status, Pageable pageable);
    List<TaskAppeal> findByTaskId(Long taskId);
}
