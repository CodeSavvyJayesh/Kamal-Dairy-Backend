package com.kamaldairy.kamal_dairy_backend.controller;

import com.kamaldairy.kamal_dairy_backend.dto.AdminReviewResponse;
import com.kamaldairy.kamal_dairy_backend.dto.PageResponse;
import com.kamaldairy.kamal_dairy_backend.dto.ReviewModerationRequest;
import com.kamaldairy.kamal_dairy_backend.dto.ReviewStatsResponse;
import com.kamaldairy.kamal_dairy_backend.service.ReviewService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Review moderation. ADMIN only - enforced here and again in SecurityConfig. */
@RestController
@RequestMapping("/api/admin/reviews")
@PreAuthorize("hasRole('ADMIN')")
public class AdminReviewController {

    private final ReviewService reviewService;

    public AdminReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    /** filter = ALL | LOW (1-2 stars) | UNANSWERED | HIDDEN. Newest first. */
    @GetMapping
    public PageResponse<AdminReviewResponse> list(
            @RequestParam(name = "filter", defaultValue = "ALL") String filter,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return reviewService.adminList(filter, page, size);
    }

    @GetMapping("/stats")
    public ReviewStatsResponse stats() {
        return reviewService.stats();
    }

    /** Takes the review off the product page and out of its rating. {"text": reason} */
    @PostMapping("/{id}/hide")
    public AdminReviewResponse hide(@PathVariable("id") Long id,
                                    @RequestBody(required = false) ReviewModerationRequest request) {
        return reviewService.hide(id, request == null ? null : request.text());
    }

    @PostMapping("/{id}/show")
    public AdminReviewResponse show(@PathVariable("id") Long id) {
        return reviewService.show(id);
    }

    /** Public reply under the review. {"text": ""} removes it. */
    @PutMapping("/{id}/reply")
    public AdminReviewResponse reply(@PathVariable("id") Long id,
                                     @RequestBody(required = false) ReviewModerationRequest request) {
        return reviewService.reply(id, request == null ? null : request.text());
    }
}
