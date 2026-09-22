package com.kamaldairy.kamal_dairy_backend.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A standing instruction: "deliver N of product P on this schedule, to this
 * address, and charge my wallet each time".
 *
 * It holds no money and never changes a delivery that has already been
 * generated. Deliveries are produced from it each night by SubscriptionEngine,
 * one immutable SubscriptionDelivery row per date.
 */
@Entity
@Table(
        name = "subscriptions",
        indexes = {
                @Index(name = "idx_sub_user", columnList = "user_email"),
                @Index(name = "idx_sub_status_start", columnList = "status, start_date")
        }
)
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_email", nullable = false, length = 191)
    private String userEmail;

    @Column(name = "product_id", nullable = false)
    private Integer productId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "product_image_url", length = 500)
    private String productImageUrl;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionFrequency frequency;

    /** Bit 0 = Monday ... bit 6 = Sunday. Only used by CUSTOM_DAYS and WEEKLY. */
    @Column(name = "days_of_week_mask", nullable = false)
    private int daysOfWeekMask;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DeliverySlot slot;

    /** Anchor date: alternate-day and 30-day cycles are counted from here. */
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private SubscriptionStatus status;

    @Column(name = "vacation_start")
    private LocalDate vacationStart;

    @Column(name = "vacation_end")
    private LocalDate vacationEnd;

    @Column(name = "delivery_name", nullable = false, length = 80)
    private String deliveryName;

    @Column(name = "delivery_phone", nullable = false, length = 15)
    private String deliveryPhone;

    @Column(name = "delivery_address", nullable = false)
    private String deliveryAddress;

    @Column(name = "delivery_city", nullable = false, length = 60)
    private String deliveryCity;

    @Column(name = "delivery_pincode", nullable = false, length = 6)
    private String deliveryPincode;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    public Subscription() {}

    public boolean isOnVacation(LocalDate date) {
        return vacationStart != null && vacationEnd != null
                && !date.isBefore(vacationStart) && !date.isAfter(vacationEnd);
    }

    public String fullAddress() {
        return deliveryAddress + ", " + deliveryCity + " - " + deliveryPincode;
    }

    public Long getId() { return id; }
    public String getUserEmail() { return userEmail; }
    public Integer getProductId() { return productId; }
    public String getProductName() { return productName; }
    public String getProductImageUrl() { return productImageUrl; }
    public int getQuantity() { return quantity; }
    public SubscriptionFrequency getFrequency() { return frequency; }
    public int getDaysOfWeekMask() { return daysOfWeekMask; }
    public DeliverySlot getSlot() { return slot; }
    public LocalDate getStartDate() { return startDate; }
    public SubscriptionStatus getStatus() { return status; }
    public LocalDate getVacationStart() { return vacationStart; }
    public LocalDate getVacationEnd() { return vacationEnd; }
    public String getDeliveryName() { return deliveryName; }
    public String getDeliveryPhone() { return deliveryPhone; }
    public String getDeliveryAddress() { return deliveryAddress; }
    public String getDeliveryCity() { return deliveryCity; }
    public String getDeliveryPincode() { return deliveryPincode; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }

    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }
    public void setProductId(Integer productId) { this.productId = productId; }
    public void setProductName(String productName) { this.productName = productName; }
    public void setProductImageUrl(String productImageUrl) { this.productImageUrl = productImageUrl; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public void setFrequency(SubscriptionFrequency frequency) { this.frequency = frequency; }
    public void setDaysOfWeekMask(int daysOfWeekMask) { this.daysOfWeekMask = daysOfWeekMask; }
    public void setSlot(DeliverySlot slot) { this.slot = slot; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public void setStatus(SubscriptionStatus status) { this.status = status; }
    public void setVacationStart(LocalDate vacationStart) { this.vacationStart = vacationStart; }
    public void setVacationEnd(LocalDate vacationEnd) { this.vacationEnd = vacationEnd; }
    public void setDeliveryName(String deliveryName) { this.deliveryName = deliveryName; }
    public void setDeliveryPhone(String deliveryPhone) { this.deliveryPhone = deliveryPhone; }
    public void setDeliveryAddress(String deliveryAddress) { this.deliveryAddress = deliveryAddress; }
    public void setDeliveryCity(String deliveryCity) { this.deliveryCity = deliveryCity; }
    public void setDeliveryPincode(String deliveryPincode) { this.deliveryPincode = deliveryPincode; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public void setCancelledAt(LocalDateTime cancelledAt) { this.cancelledAt = cancelledAt; }
}
