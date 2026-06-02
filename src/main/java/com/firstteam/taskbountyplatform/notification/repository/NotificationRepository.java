package com.firstteam.taskbountyplatform.notification.repository;

import com.firstteam.taskbountyplatform.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Page<Notification> findByReceiverIdOrderByCreatedAtDesc(Long receiverId, Pageable pageable);

    List<Notification> findTop5ByReceiverIdAndIsReadFalseOrderByCreatedAtDesc(Long receiverId);

    long countByReceiverIdAndIsReadFalse(Long receiverId);

    Page<Notification> findByReceiverIdAndType(Long receiverId, String type, Pageable pageable);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = :now WHERE n.id IN :ids")
    int markAsRead(@Param("ids") List<Long> ids, @Param("now") LocalDateTime now);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.id IN :ids AND n.receiverId = :userId")
    int deleteByIds(@Param("ids") List<Long> ids, @Param("userId") Long userId);

    // 检查某用户在某时间段内同类型通知数量（防刷屏）
    long countByReceiverIdAndTypeAndTaskIdAndCreatedAtAfter(
            Long receiverId, String type, Long taskId, LocalDateTime after);
}
