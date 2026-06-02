package com.firstteam.taskbountyplatform.task.repository;

import com.firstteam.taskbountyplatform.task.entity.TaskCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaskCategoryRepository extends JpaRepository<TaskCategory, Long> {
    Optional<TaskCategory> findByName(String name);
    boolean existsByName(String name);
    List<TaskCategory> findByEnabledTrueOrderBySortOrderAsc();
    List<TaskCategory> findByEnabledTrue();
}
