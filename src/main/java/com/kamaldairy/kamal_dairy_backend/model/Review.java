package com.kamaldairy.kamal_dairy_backend.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * One customer's review of one product. Only customers who received the
 * product can write one (checked by ReviewService), so every review is a
 * verified purchase. Unique per (product, customer): writing again edits it.
 */
@Entity
@Table(
        name = "product_reviews",
        uniqueConstraints = @UniqueConstraint(name = "uk_review_product_user", columnNames = {"product_id", "user_email"}),
        indexes = {
                @Index(name = "idx_review_product_status", columnList = "product_id, status, created_at"),
                @Index(name = "idx_review_status_created", columnList = "status, created_at")
        }
)
public class Review {

    public static final String VIA_ORDER = "ORDER";
    public static final String VIA_SUBSCRIPTION = "SUBSCRIPTION";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Integer productId;

    /** Snapshot, so admin lists still read well if the product is renamed or removed. */
    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "user_email", nullable = false, length = 191)
    private String userEmail;

    /** "Alice S." - first name and last initial, never the full name or email. */
    @Column(name = "author_name", nullable = false, length = 60)
    private String authorName;

    @Column(nullable = false)
    private int rating;

    @Column(length = 80)
    private String title;

    @Column(length = 1000)
    private String body;

    /** ORDER or SUBSCRIPTION: how the customer received the product. */
    @Column(name = "verified_via", nullable = false, length = 12)
    private String verifiedVia;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ReviewStatus status;

    @Column(name = "hidden_reason", length = 200)
    private String hiddenReason;

    @Column(length = 500)
    private String reply;

    @Column(name = "replied_at")
    private LocalDateTime repliedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Review() {}

    public boolean isPublished() { return status == ReviewStatus.PUBLISHED; }

    public Long getId() { return id; }
    public Integer getProductId() { return productId; }
    public String getProductName() { return productName; }
    public String getUserEmail() { return userEmail; }
    public String getAuthorName() { return authorName; }
    public int getRating() { return rating; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getVerifiedVia() { return verifiedVia; }
    public ReviewStatus getStatus() { return status; }
    public String getHiddenReason() { return hiddenReason; }
    public String getReply() { return reply; }
    public LocalDateTime getRepliedAt() { return repliedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setProductId(Integer productId) { this.productId = productId; }
    public void setProductName(String productName) { this.productName = productName; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }
    public void setRating(int rating) { this.rating = rating; }
    public void setTitle(String title) { this.title = title; }
    public void setBody(String body) { this.body = body; }
    public void setVerifiedVia(String verifiedVia) { this.verifiedVia = verifiedVia; }
    public void setStatus(ReviewStatus status) { this.status = status; }
    public void setHiddenReason(String hiddenReason) { this.hiddenReason = hiddenReason; }
    public void setReply(String reply) { this.reply = reply; }
    public void setRepliedAt(LocalDateTime repliedAt) { this.repliedAt = repliedAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
