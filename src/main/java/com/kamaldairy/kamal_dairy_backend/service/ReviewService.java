package com.kamaldairy.kamal_dairy_backend.service;

import com.kamaldairy.kamal_dairy_backend.dto.*;
import com.kamaldairy.kamal_dairy_backend.exception.ApiException;
import com.kamaldairy.kamal_dairy_backend.exception.ResourceNotFoundException;
import com.kamaldairy.kamal_dairy_backend.model.*;
import com.kamaldairy.kamal_dairy_backend.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Product reviews.
 *
 *  - Verified buyers only: the customer must have a delivered cart order or a
 *    delivered subscription delivery containing the product.
 *  - One review per product per customer. Writing again edits it.
 *  - Every write locks the product row first, then keeps the product's
 *    rating count and total in step inside the same transaction. Reviews
 *    arriving at the same moment therefore queue up and the average can
 *    never drift from the reviews behind it.
 *  - Hidden reviews are left out of the average and the public list.
 */
@Service
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    public static final int MAX_TITLE = 80;
    public static final int MAX_BODY = 1000;
    public static final int MAX_REPLY = 500;
    private static final int MAX_REASON = 200;
    private static final int MAX_PAGE = 50;

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final SubscriptionDeliveryRepository deliveryRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final DeliveryCalendar calendar;

    public ReviewService(ReviewRepository reviewRepository,
                         ProductRepository productRepository,
                         OrderRepository orderRepository,
                         SubscriptionDeliveryRepository deliveryRepository,
                         UserRepository userRepository,
                         EmailService emailService,
                         DeliveryCalendar calendar) {
        this.reviewRepository = reviewRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.deliveryRepository = deliveryRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.calendar = calendar;
    }

    // ============================================================== public

    @Transactional(readOnly = true)
    public Product product(Integer productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product"));
    }

    @Transactional(readOnly = true)
    public ProductReviewsResponse productReviews(Integer productId, String sort, Integer stars, int page, int size) {
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Product");
        }

        Map<Integer, Long> byStars = new HashMap<>();
        long count = 0, total = 0;
        for (Object[] row : reviewRepository.distribution(productId, ReviewStatus.PUBLISHED)) {
            int r = ((Number) row[0]).intValue();
            long n = ((Number) row[1]).longValue();
            byStars.put(r, n);
            count += n;
            total += r * n;
        }
        List<ProductReviewsResponse.Bucket> distribution = new ArrayList<>();
        for (int s = 5; s >= 1; s--) {
            distribution.add(new ProductReviewsResponse.Bucket(s, byStars.getOrDefault(s, 0L)));
        }
        Double average = count == 0 ? null : Math.round(10.0 * total / count) / 10.0;

        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), MAX_PAGE), sortOf(sort));
        Page<Review> reviews = stars == null
                ? reviewRepository.findByProductIdAndStatus(productId, ReviewStatus.PUBLISHED, pageable)
                : reviewRepository.findByProductIdAndStatusAndRating(productId, ReviewStatus.PUBLISHED,
                        requireStars(stars), pageable);

        return new ProductReviewsResponse(productId, average, count, distribution,
                PageResponse.of(reviews.map(ReviewService::toResponse)));
    }

    // ============================================================ customer

    @Transactional(readOnly = true)
    public ReviewEligibilityResponse eligibility(String email, Integer productId) {
        if (productId == null || !productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Product");
        }
        Review mine = reviewRepository.findByProductIdAndUserEmail(productId, email).orElse(null);
        if (mine != null) {
            return new ReviewEligibilityResponse(productId, true, null, toResponse(mine));
        }
        if (receivedVia(email, productId) == null) {
            return new ReviewEligibilityResponse(productId, false,
                    "Only customers who received this product can review it. Once your order is delivered, "
                            + "you can rate it here or from My Orders.", null);
        }
        return new ReviewEligibilityResponse(productId, true, null, null);
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> mine(String email) {
        return reviewRepository.findByUserEmailOrderByUpdatedAtDesc(email).stream()
                .map(ReviewService::toResponse).toList();
    }

    /** Creates the caller's review, or edits it if they already wrote one. */
    @Transactional
    public ReviewResponse write(String email, ReviewRequest request) {
        if (request == null || request.productId() == null) {
            throw bad("Choose a product to review.");
        }
        int rating = requireStars(request.rating());
        String title = cleanLine(request.title(), MAX_TITLE, "The title");
        String body = cleanText(request.body(), MAX_BODY, "The review");

        // Lock first: every later read in this transaction sees the latest committed state.
        Product product = productRepository.lockOne(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Product"));

        Review review = reviewRepository.findByProductIdAndUserEmail(product.getId(), email).orElse(null);
        var now = calendar.now();

        if (review == null) {
            String via = receivedVia(email, product.getId());
            if (via == null) {
                throw new ApiException("You can review " + product.getName()
                        + " once you have received it.", HttpStatus.FORBIDDEN);
            }
            review = new Review();
            review.setProductId(product.getId());
            review.setUserEmail(email);
            review.setVerifiedVia(via);
            review.setStatus(ReviewStatus.PUBLISHED);
            review.setCreatedAt(now);
            product.applyRatingChange(1, rating);
        } else if (review.isPublished()) {
            product.applyRatingChange(0, rating - review.getRating());
        }
        // An edit to a hidden review stays hidden: the dairy's decision stands until it is shown again.

        review.setProductName(product.getName());
        review.setAuthorName(authorName(email));
        review.setRating(rating);
        review.setTitle(title);
        review.setBody(body);
        review.setUpdatedAt(now);

        return toResponse(reviewRepository.save(review));
    }

    @Transactional
    public void delete(String email, Long reviewId) {
        Review found = reviewRepository.findById(reviewId)
                .filter(r -> r.getUserEmail().equalsIgnoreCase(email))
                .orElseThrow(() -> new ResourceNotFoundException("Review"));

        Product product = productRepository.lockOne(found.getProductId()).orElse(null);
        Review review = reviewRepository.findForUpdate(found.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Review"));

        if (product != null && review.isPublished()) {
            product.applyRatingChange(-1, -review.getRating());
        }
        reviewRepository.delete(review);
    }

    // =============================================================== admin

    @Transactional(readOnly = true)
    public PageResponse<AdminReviewResponse> adminList(String filter, int page, int size) {
        Pageable p = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), MAX_PAGE),
                Sort.by(Sort.Direction.DESC, "createdAt", "id"));

        String f = filter == null ? "ALL" : filter.trim().toUpperCase(Locale.ROOT);
        Page<Review> reviews = switch (f) {
            case "ALL" -> reviewRepository.findAll(p);
            case "LOW" -> reviewRepository.findByStatusAndRatingLessThanEqual(ReviewStatus.PUBLISHED, 2, p);
            case "UNANSWERED" -> reviewRepository.findByStatusAndReplyIsNull(ReviewStatus.PUBLISHED, p);
            case "HIDDEN" -> reviewRepository.findByStatus(ReviewStatus.HIDDEN, p);
            default -> throw bad("Unknown filter: " + filter);
        };
        return PageResponse.of(reviews.map(ReviewService::toAdmin));
    }

    @Transactional(readOnly = true)
    public ReviewStatsResponse stats() {
        Double avg = reviewRepository.averageRating(ReviewStatus.PUBLISHED);
        return new ReviewStatsResponse(
                reviewRepository.countByStatus(ReviewStatus.PUBLISHED),
                reviewRepository.countByStatus(ReviewStatus.HIDDEN),
                avg == null ? null : Math.round(avg * 10) / 10.0,
                reviewRepository.countByStatusAndRatingLessThanEqual(ReviewStatus.PUBLISHED, 2),
                reviewRepository.countByStatusAndReplyIsNull(ReviewStatus.PUBLISHED));
    }

    @Transactional
    public AdminReviewResponse hide(Long reviewId, String reason) {
        Review review = lockForModeration(reviewId);
        if (!review.isPublished()) {
            throw new ApiException("This review is already hidden.", HttpStatus.CONFLICT);
        }
        productRepository.lockOne(review.getProductId())
                .ifPresent(p -> p.applyRatingChange(-1, -review.getRating()));

        review.setStatus(ReviewStatus.HIDDEN);
        review.setHiddenReason(cleanLine(reason, MAX_REASON, "The reason"));
        log.info("Review {} hidden", review.getId());
        return toAdmin(review);
    }

    @Transactional
    public AdminReviewResponse show(Long reviewId) {
        Review review = lockForModeration(reviewId);
        if (review.isPublished()) {
            throw new ApiException("This review is already visible.", HttpStatus.CONFLICT);
        }
        productRepository.lockOne(review.getProductId())
                .ifPresent(p -> p.applyRatingChange(1, review.getRating()));

        review.setStatus(ReviewStatus.PUBLISHED);
        review.setHiddenReason(null);
        return toAdmin(review);
    }

    /** Public reply from the dairy under the review. Blank removes it. */
    @Transactional
    public AdminReviewResponse reply(Long reviewId, String text) {
        Review review = reviewRepository.findForUpdate(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review"));

        String reply = cleanText(text, MAX_REPLY, "The reply");
        boolean changed = reply != null && !reply.equals(review.getReply());

        review.setReply(reply);
        review.setRepliedAt(reply == null ? null : calendar.now());

        if (changed) {
            emailService.sendReviewReply(review.getUserEmail(), review.getProductName(), reply);
        }
        return toAdmin(review);
    }

    // ============================================================= helpers

    /** Locks in the same order as customer writes (product, then review) so the two never deadlock. */
    private Review lockForModeration(Long reviewId) {
        Review found = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review"));
        productRepository.lockOne(found.getProductId());
        return reviewRepository.findForUpdate(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review"));
    }

    /** ORDER or SUBSCRIPTION if the customer has received this product, otherwise null. */
    private String receivedVia(String email, Integer productId) {
        if (orderRepository.countReceived(email, productId, OrderStatus.DELIVERED) > 0) {
            return Review.VIA_ORDER;
        }
        if (deliveryRepository.countByUserEmailAndProductIdAndStatus(email, productId, DeliveryStatus.DELIVERED) > 0) {
            return Review.VIA_SUBSCRIPTION;
        }
        return null;
    }

    /** "Alice Shah" -> "Alice S." */
    private String authorName(String email) {
        String name = userRepository.findByEmail(email).map(User::getName).orElse(null);
        if (name == null || name.isBlank()) {
            return "Kamal Dairy customer";
        }
        String[] parts = name.trim().split("\\s+");
        String first = parts[0].length() > 30 ? parts[0].substring(0, 30) : parts[0];
        return parts.length > 1
                ? first + " " + parts[parts.length - 1].substring(0, 1).toUpperCase(Locale.ROOT) + "."
                : first;
    }

    private static Sort sortOf(String sort) {
        String s = sort == null ? "recent" : sort.trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "highest" -> Sort.by(Sort.Order.desc("rating"), Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
            case "lowest" -> Sort.by(Sort.Order.asc("rating"), Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
            case "recent" -> Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
            default -> throw bad("Unknown sort: " + sort);
        };
    }

    private static int requireStars(Integer stars) {
        if (stars == null || stars < 1 || stars > 5) {
            throw bad("Choose a rating from 1 to 5 stars.");
        }
        return stars;
    }

    /** One line: control characters out, spaces collapsed. Blank becomes null. */
    private static String cleanLine(String s, int max, String what) {
        if (s == null) return null;
        String v = s.replaceAll("[\\p{Cntrl}]", " ").trim().replaceAll("\\s+", " ");
        if (v.isEmpty()) return null;
        if (v.length() > max) throw bad(what + " can be at most " + max + " characters.");
        return v;
    }

    /** Paragraphs kept, at most one blank line between them, control characters out. */
    private static String cleanText(String s, int max, String what) {
        if (s == null) return null;
        String v = s.replace("\r\n", "\n").replace('\r', '\n')
                .replaceAll("[\\p{Cntrl}&&[^\\n]]", " ")
                .replaceAll("[ \\t]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        if (v.isEmpty()) return null;
        if (v.length() > max) throw bad(what + " can be at most " + max + " characters.");
        return v;
    }

    private static ApiException bad(String message) {
        return new ApiException(message, HttpStatus.BAD_REQUEST);
    }

    static ReviewResponse toResponse(Review r) {
        return new ReviewResponse(r.getId(), r.getProductId(), r.getProductName(), r.getRating(), r.getTitle(),
                r.getBody(), r.getAuthorName(), r.getVerifiedVia(), r.getStatus().name(),
                r.getUpdatedAt() != null && r.getCreatedAt() != null && r.getUpdatedAt().isAfter(r.getCreatedAt()),
                r.getReply(), r.getRepliedAt(), r.getCreatedAt(), r.getUpdatedAt());
    }

    static AdminReviewResponse toAdmin(Review r) {
        return new AdminReviewResponse(r.getId(), r.getProductId(), r.getProductName(), r.getUserEmail(),
                r.getAuthorName(), r.getRating(), r.getTitle(), r.getBody(), r.getVerifiedVia(),
                r.getStatus().name(), r.getHiddenReason(), r.getReply(), r.getRepliedAt(),
                r.getCreatedAt(), r.getUpdatedAt());
    }
}
