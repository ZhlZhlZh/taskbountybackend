package com.firstteam.taskbountyplatform.user.repository;

import com.firstteam.taskbountyplatform.common.enums.AccountRole;
import com.firstteam.taskbountyplatform.common.enums.UserStatus;
import com.firstteam.taskbountyplatform.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByStudentNo(String studentNo);
    Optional<User> findByNickname(String nickname);
    boolean existsByStudentNo(String studentNo);
    boolean existsByNickname(String nickname);

    Page<User> findByAccountStatus(UserStatus status, Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.studentNo LIKE %:keyword% OR u.nickname LIKE %:keyword%")
    Page<User> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    List<User> findByGraduatedTrueAndAccountStatus(UserStatus status);

    long countByAccountStatus(UserStatus status);

    @Query("SELECT COUNT(u) FROM User u WHERE u.createdAt >= :since")
    long countNewUsersSince(@Param("since") LocalDateTime since);

    List<User> findByCreditScoreLessThan(int threshold);

    @Query("SELECT u FROM User u WHERE u.creditScore BETWEEN :min AND :max")
    Page<User> findByCreditScoreRange(@Param("min") int min, @Param("max") int max, Pageable pageable);

    @Query("SELECT u FROM User u WHERE (:status IS NULL OR u.accountStatus = :status) " +
           "AND (:minScore IS NULL OR u.creditScore >= :minScore) " +
           "AND (:maxScore IS NULL OR u.creditScore <= :maxScore)")
    Page<User> filterUsers(@Param("status") UserStatus status,
                          @Param("minScore") Integer minScore,
                          @Param("maxScore") Integer maxScore,
                          Pageable pageable);

    Optional<User> findByNicknameAndIdNot(String nickname, Long id);

    List<User> findByRole(AccountRole role);
}
