package com.firstteam.taskbountyplatform.review.repository;

import com.firstteam.taskbountyplatform.review.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByTaskId(Long taskId);
    Optional<Review> findByTaskIdAndReviewerId(Long taskId, Long reviewerId);
    Optional<Review> findByTaskIdAndReviewerIdAndReviewType(Long taskId, Long reviewerId, String reviewType);

    Page<Review> findByRevieweeIdOrderByCreatedAtDesc(Long revieweeId, Pageable pageable);

    // 好评率统计：4星及以上视为好评
    @Query("SELECT COUNT(r) FROM Review r WHERE r.revieweeId = :userId AND r.stars >= 4 AND r.reviewType = :reviewType")
    long countGoodReviews(@Param("userId") Long userId, @Param("reviewType") String reviewType);

    @Query("SELECT COUNT(r) FROM Review r WHERE r.revieweeId = :userId AND r.reviewType = :reviewType")
    long countTotalReviews(@Param("userId") Long userId, @Param("reviewType") String reviewType);
}
