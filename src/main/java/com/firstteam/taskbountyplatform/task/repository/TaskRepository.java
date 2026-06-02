package com.firstteam.taskbountyplatform.task.repository;

import com.firstteam.taskbountyplatform.common.enums.TaskStatus;
import com.firstteam.taskbountyplatform.task.entity.Task;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {

    // 发布者的任务
    Page<Task> findByPublisherIdOrderByPublishedAtDesc(Long publisherId, Pageable pageable);

    List<Task> findByPublisherId(Long publisherId);

    Page<Task> findByPublisherIdAndStatus(Long publisherId, TaskStatus status, Pageable pageable);

    // 公共任务大厅 - 已发布且未超过自动取消时间
    @Query("SELECT t FROM Task t WHERE t.status = 'PUBLISHED' AND t.publishedAt >= :cutoffDate")
    Page<Task> findAvailableTasks(@Param("cutoffDate") LocalDateTime cutoffDate, Pageable pageable);

    // 进行中的任务（中标者是某人）
    List<Task> findByWinnerIdAndStatus(Long winnerId, TaskStatus status);

    // 中标者的任务
    List<Task> findByWinnerId(Long winnerId);

    // 超时未接单的任务
    @Query("SELECT t FROM Task t WHERE t.status = 'PUBLISHED' AND t.publishedAt < :cutoffDate")
    List<Task> findExpiredPublishedTasks(@Param("cutoffDate") LocalDateTime cutoffDate);

    // 待确认超时的任务
    @Query("SELECT t FROM Task t WHERE t.status = 'PENDING_CONFIRMATION'")
    List<Task> findTasksPendingConfirmation();

    // 即将截止的任务提醒
    @Query("SELECT t FROM Task t WHERE t.status = 'IN_PROGRESS' AND t.deadlineAt BETWEEN :now AND :warningTime")
    List<Task> findTasksNearingDeadline(@Param("now") LocalDateTime now, @Param("warningTime") LocalDateTime warningTime);

    // 已超时的进行中任务
    @Query("SELECT t FROM Task t WHERE t.status = 'IN_PROGRESS' AND t.deadlineAt < :now")
    List<Task> findOverdueTasks(@Param("now") LocalDateTime now);

    // 按分类统计
    long countByCategoryId(Long categoryId);
    long countByCategoryIdAndStatus(Long categoryId, TaskStatus status);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.status = :status")
    long countByStatus(@Param("status") TaskStatus status);

    long count();

    @Query("SELECT COUNT(t) FROM Task t WHERE t.createdAt >= :since")
    long countNewTasksSince(@Param("since") LocalDateTime since);

    // 热门任务 - 最近7天申请最多的任务
    @Query(value = "SELECT t.* FROM tasks t " +
           "JOIN (SELECT task_id, COUNT(*) as cnt FROM task_applications " +
           "WHERE applied_at >= :since GROUP BY task_id ORDER BY cnt DESC LIMIT :limit) ta " +
           "ON t.id = ta.task_id WHERE t.status = 'PUBLISHED' ORDER BY ta.cnt DESC",
           nativeQuery = true)
    List<Task> findHotTasks(@Param("since") LocalDateTime since, @Param("limit") int limit);

    // 推荐任务 - 用户频繁交互的分类
    @Query("SELECT t FROM Task t WHERE t.status = 'PUBLISHED' AND t.categoryId IN :categoryIds " +
           "ORDER BY t.deadlineMinutes ASC")
    List<Task> findRecommendedTasks(@Param("categoryIds") List<Long> categoryIds, Pageable pageable);
}
