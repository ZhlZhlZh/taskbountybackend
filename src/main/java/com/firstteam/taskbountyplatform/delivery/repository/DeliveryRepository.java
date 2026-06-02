package com.firstteam.taskbountyplatform.delivery.repository;

import com.firstteam.taskbountyplatform.delivery.entity.Delivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeliveryRepository extends JpaRepository<Delivery, Long> {
    List<Delivery> findByTaskIdOrderBySubmitTimeDesc(Long taskId);
    Optional<Delivery> findByTaskIdAndWorkerId(Long taskId, Long workerId);
    long countByTaskId(Long taskId);
    Optional<Delivery> findFirstByTaskIdOrderBySubmitTimeDesc(Long taskId);
}
