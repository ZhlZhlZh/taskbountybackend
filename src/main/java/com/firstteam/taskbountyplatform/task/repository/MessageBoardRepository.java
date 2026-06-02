package com.firstteam.taskbountyplatform.task.repository;

import com.firstteam.taskbountyplatform.task.entity.MessageBoard;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MessageBoardRepository extends JpaRepository<MessageBoard, Long> {
    List<MessageBoard> findByTaskIdOrderBySentAtAsc(Long taskId);
    Page<MessageBoard> findByTaskIdOrderBySentAtAsc(Long taskId, Pageable pageable);

    // 统计用户最近一分钟发送的消息数
    long countByTaskIdAndSenderIdAndSentAtAfter(Long taskId, Long senderId, LocalDateTime after);

    // 清理过期聊天记录
    void deleteByTaskIdAndSentAtBefore(Long taskId, LocalDateTime before);
}
