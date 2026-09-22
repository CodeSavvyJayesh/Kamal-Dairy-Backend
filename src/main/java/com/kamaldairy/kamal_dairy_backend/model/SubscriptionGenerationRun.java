package com.kamaldairy.kamal_dairy_backend.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * "Deliveries for date D have been generated."
 *
 * Without this, the hourly catch-up would regenerate today's deliveries every
 * time a customer resumed a subscription mid-morning - creating a delivery
 * after the 11 PM cutoff. With it, each date is closed exactly once, and a
 * server that slept through the cutoff still catches up when it wakes.
 */
@Entity
@Table(
        name = "subscription_generation_runs",
        uniqueConstraints = @UniqueConstraint(name = "uk_gen_run_date", columnNames = "delivery_date")
)
public class SubscriptionGenerationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "delivery_date", nullable = false)
    private LocalDate deliveryDate;

    @Column(name = "run_trigger", nullable = false, length = 20)
    private String trigger;

    @Column(nullable = false)
    private int charged;

    @Column(nullable = false)
    private int missed;

    @Column(name = "completed_at", nullable = false)
    private LocalDateTime completedAt;

    public SubscriptionGenerationRun() {}

    public SubscriptionGenerationRun(LocalDate deliveryDate, String trigger, int charged, int missed,
                                     LocalDateTime completedAt) {
        this.deliveryDate = deliveryDate;
        this.trigger = trigger;
        this.charged = charged;
        this.missed = missed;
        this.completedAt = completedAt;
    }

    public Long getId() { return id; }
    public LocalDate getDeliveryDate() { return deliveryDate; }
    public String getTrigger() { return trigger; }
    public int getCharged() { return charged; }
    public int getMissed() { return missed; }
    public LocalDateTime getCompletedAt() { return completedAt; }
}
