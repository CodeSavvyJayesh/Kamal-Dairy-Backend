package com.kamaldairy.kamal_dairy_backend.controller;

import com.kamaldairy.kamal_dairy_backend.dto.ProductReviewsResponse;
import com.kamaldairy.kamal_dairy_backend.model.Product;
import com.kamaldairy.kamal_dairy_backend.service.ReviewService;
import org.springframework.web.bind.annotation.*;

/** Public: one product and its reviews. GET /api/products/** is open in SecurityConfig. */
@RestController
@RequestMapping("/api/products")
public class ProductReviewController {

    private final ReviewService reviewService;

    public ProductReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    /** One product, with its rating. */
    @GetMapping("/{id}/details")
    public Product details(@PathVariable("id") Integer id) {
        return reviewService.product(id);
    }

    /** Rating summary and published reviews. sort = recent | highest | lowest; stars = 1..5 to filter. */
    @GetMapping("/{id}/reviews")
    public ProductReviewsResponse reviews(
            @PathVariable("id") Integer id,
            @RequestParam(name = "sort", defaultValue = "recent") String sort,
            @RequestParam(name = "stars", required = false) Integer stars,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        return reviewService.productReviews(id, sort, stars, page, size);
    }
}
