package com.kamaldairy.kamal_dairy_backend.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** A single date the customer asked us not to deliver on. */
@Entity
@Table(
        name = "subscription_skips",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_skip_sub_date", columnNames = {"subscription_id", "skip_date"})
)
public class SubscriptionSkip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subscription_id", nullable = false)
    private Long subscriptionId;

    @Column(name = "skip_date", nullable = false)
    private LocalDate skipDate;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public SubscriptionSkip() {}

    public SubscriptionSkip(Long subscriptionId, LocalDate skipDate, LocalDateTime createdAt) {
        this.subscriptionId = subscriptionId;
        this.skipDate = skipDate;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getSubscriptionId() { return subscriptionId; }
    public LocalDate getSkipDate() { return skipDate; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
