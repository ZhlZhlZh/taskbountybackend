package com.firstteam.taskbountyplatform.review.controller;

import com.firstteam.taskbountyplatform.auth.security.UserContext;
import com.firstteam.taskbountyplatform.common.response.ApiResponse;
import com.firstteam.taskbountyplatform.common.response.PageResult;
import com.firstteam.taskbountyplatform.review.dto.ReviewSubmitRequest;
import com.firstteam.taskbountyplatform.review.entity.Review;
import com.firstteam.taskbountyplatform.review.repository.ReviewRepository;
import com.firstteam.taskbountyplatform.review.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ReviewController {

    private final ReviewService reviewService;
    private final ReviewRepository reviewRepository;
    private final UserContext userContext;

    public ReviewController(ReviewService reviewService,
                            ReviewRepository reviewRepository,
                            UserContext userContext) {
        this.reviewService = reviewService;
        this.reviewRepository = reviewRepository;
        this.userContext = userContext;
    }

    /**
     * POST /api/tasks/{taskId}/reviews - Submit a review for a completed task.
     */
    @PostMapping("/tasks/{taskId}/reviews")
    public ApiResponse<Review> submitReview(@PathVariable Long taskId,
                                            @Valid @RequestBody ReviewSubmitRequest request) {
        Long userId = userContext.getCurrentUserId();
        Review review = reviewService.submitReview(taskId, userId, request);
        return ApiResponse.success("评价提交成功", review);
    }

    /**
     * GET /api/tasks/{taskId}/reviews - Get all reviews for a task (paginated manually).
     */
    @GetMapping("/tasks/{taskId}/reviews")
    public ApiResponse<PageResult<Review>> getReviewsForTask(@PathVariable Long taskId,
                                                              @RequestParam(defaultValue = "1") int page,
                                                              @RequestParam(defaultValue = "15") int size) {
        List<Review> allReviews = reviewRepository.findByTaskId(taskId);

        // Manual pagination from in-memory list
        int start = Math.min((page - 1) * size, allReviews.size());
        int end = Math.min(start + size, allReviews.size());
        List<Review> pageContent = start < end ? allReviews.subList(start, end) : List.of();

        PageResult<Review> result = new PageResult<>(pageContent, page, size, allReviews.size());
        return ApiResponse.success(result);
    }

    /**
     * GET /api/users/{userId}/reviews - Get paginated reviews received by a user (public).
     */
    @GetMapping("/users/{userId}/reviews")
    public ApiResponse<PageResult<Review>> getReviewsForUser(@PathVariable Long userId,
                                                              @RequestParam(defaultValue = "1") int page,
                                                              @RequestParam(defaultValue = "15") int size) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by("createdAt").descending());
        Page<Review> reviewPage = reviewService.getReviewsForUser(userId, pageable);
        PageResult<Review> result = new PageResult<>(
                reviewPage.getContent(), page, size, reviewPage.getTotalElements());
        return ApiResponse.success(result);
    }

    /**
     * GET /api/tasks/{taskId}/my-review - Get current user's review for a task.
     */
    @GetMapping("/tasks/{taskId}/my-review")
    public ApiResponse<Review> getMyReview(@PathVariable Long taskId) {
        Long userId = userContext.getCurrentUserId();
        Review review = reviewService.getReviewForTask(taskId, userId);
        return ApiResponse.success(review);
    }
}
