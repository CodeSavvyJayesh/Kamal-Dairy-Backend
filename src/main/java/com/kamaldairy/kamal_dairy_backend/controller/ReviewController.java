package com.kamaldairy.kamal_dairy_backend.controller;

import com.kamaldairy.kamal_dairy_backend.dto.ReviewEligibilityResponse;
import com.kamaldairy.kamal_dairy_backend.dto.ReviewRequest;
import com.kamaldairy.kamal_dairy_backend.dto.ReviewResponse;
import com.kamaldairy.kamal_dairy_backend.service.ReviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Signed-in customers: write, edit and delete their own reviews. The author is always the caller. */
@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/eligibility")
    public ReviewEligibilityResponse eligibility(@RequestParam(name = "productId") Integer productId,
                                                 Authentication auth) {
        return reviewService.eligibility(auth.getName(), productId);
    }

    @GetMapping("/mine")
    public List<ReviewResponse> mine(Authentication auth) {
        return reviewService.mine(auth.getName());
    }

    /** Creates the caller's review of the product, or edits it if one exists. */
    @PostMapping
    public ReviewResponse write(@RequestBody ReviewRequest request, Authentication auth) {
        return reviewService.write(auth.getName(), request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id, Authentication auth) {
        reviewService.delete(auth.getName(), id);
        return ResponseEntity.noContent().build();
    }
}
