package com.firstteam.taskbountyplatform.credit.repository;

import com.firstteam.taskbountyplatform.credit.entity.CreditRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CreditRecordRepository extends JpaRepository<CreditRecord, Long> {
    Page<CreditRecord> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
