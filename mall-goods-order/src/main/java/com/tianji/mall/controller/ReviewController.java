package com.tianji.mall.controller;

import com.tianji.common.result.R;
import com.tianji.common.util.JwtUtil;
import com.tianji.mall.dto.PendingReviewResponse;
import com.tianji.mall.dto.ReviewCreateRequest;
import com.tianji.mall.dto.ReviewResponse;
import com.tianji.mall.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/review")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;
    private final JwtUtil jwtUtil;

    @PostMapping
    public R<Void> create(@RequestHeader("Authorization") String authHeader,
                          @Valid @RequestBody ReviewCreateRequest req) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        reviewService.createReview(userId, req);
        return R.ok();
    }

    @GetMapping("/product/{productId}")
    public R<List<ReviewResponse>> productReviews(@PathVariable("productId") Long productId,
                                                   @RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return R.ok(reviewService.getProductReviews(productId, page, size));
    }

    @GetMapping("/my")
    public R<List<ReviewResponse>> myReviews(@RequestHeader("Authorization") String authHeader,
                                              @RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        return R.ok(reviewService.getMyReviews(userId, page, size));
    }

    @GetMapping("/pending")
    public R<List<PendingReviewResponse>> pendingReviews(@RequestHeader("Authorization") String authHeader,
                                                           @RequestParam(defaultValue = "1") int page,
                                                           @RequestParam(defaultValue = "20") int size) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        return R.ok(reviewService.getPendingReviews(userId, page, size));
    }
}
