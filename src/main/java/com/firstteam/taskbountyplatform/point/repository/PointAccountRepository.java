package com.firstteam.taskbountyplatform.point.repository;

import com.firstteam.taskbountyplatform.point.entity.PointAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PointAccountRepository extends JpaRepository<PointAccount, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT pa FROM PointAccount pa WHERE pa.userId = :userId")
    Optional<PointAccount> findByUserIdForUpdate(@Param("userId") Long userId);

    Optional<PointAccount> findByUserId(Long userId);
}
