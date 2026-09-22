package com.kamaldairy.kamal_dairy_backend.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One generated delivery for one subscription on one date.
 *
 * The unique (subscription_id, delivery_date) constraint is what makes the
 * nightly job safe to run any number of times: a second attempt for the same
 * day fails to insert and its wallet debit rolls back with it, so a customer
 * can never be charged twice for the same morning's milk.
 *
 * Price, product name and address are snapshotted here on purpose - later
 * edits to the product or subscription must not rewrite history.
 */
@Entity
@Table(
        name = "subscription_deliveries",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_delivery_sub_date", columnNames = {"subscription_id", "delivery_date"}),
        indexes = {
                @Index(name = "idx_del_date", columnList = "delivery_date"),
                @Index(name = "idx_del_user_date", columnList = "user_email, delivery_date")
        }
)
public class SubscriptionDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subscription_id", nullable = false)
    private Long subscriptionId;

    @Column(name = "user_email", nullable = false, length = 191)
    private String userEmail;

    @Column(name = "product_id", nullable = false)
    private Integer productId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price_paise", nullable = false)
    private long unitPricePaise;

    @Column(name = "discount_percent", nullable = false)
    private int discountPercent;

    @Column(name = "amount_paise", nullable = false)
    private long amountPaise;

    @Column(name = "delivery_date", nullable = false)
    private LocalDate deliveryDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DeliverySlot slot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private DeliveryStatus status;

    @Column(length = 255)
    private String note;

    @Column(name = "wallet_transaction_id")
    private Long walletTransactionId;

    @Column(name = "refund_transaction_id")
    private Long refundTransactionId;

    @Column(name = "delivery_name", length = 80)
    private String deliveryName;

    @Column(name = "delivery_phone", length = 15)
    private String deliveryPhone;

    @Column(name = "delivery_address", length = 400)
    private String deliveryAddress;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public SubscriptionDelivery() {}

    public Long getId() { return id; }
    public Long getSubscriptionId() { return subscriptionId; }
    public String getUserEmail() { return userEmail; }
    public Integer getProductId() { return productId; }
    public String getProductName() { return productName; }
    public int getQuantity() { return quantity; }
    public long getUnitPricePaise() { return unitPricePaise; }
    public int getDiscountPercent() { return discountPercent; }
    public long getAmountPaise() { return amountPaise; }
    public LocalDate getDeliveryDate() { return deliveryDate; }
    public DeliverySlot getSlot() { return slot; }
    public DeliveryStatus getStatus() { return status; }
    public String getNote() { return note; }
    public Long getWalletTransactionId() { return walletTransactionId; }
    public Long getRefundTransactionId() { return refundTransactionId; }
    public String getDeliveryName() { return deliveryName; }
    public String getDeliveryPhone() { return deliveryPhone; }
    public String getDeliveryAddress() { return deliveryAddress; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setSubscriptionId(Long subscriptionId) { this.subscriptionId = subscriptionId; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }
    public void setProductId(Integer productId) { this.productId = productId; }
    public void setProductName(String productName) { this.productName = productName; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public void setUnitPricePaise(long unitPricePaise) { this.unitPricePaise = unitPricePaise; }
    public void setDiscountPercent(int discountPercent) { this.discountPercent = discountPercent; }
    public void setAmountPaise(long amountPaise) { this.amountPaise = amountPaise; }
    public void setDeliveryDate(LocalDate deliveryDate) { this.deliveryDate = deliveryDate; }
    public void setSlot(DeliverySlot slot) { this.slot = slot; }
    public void setStatus(DeliveryStatus status) { this.status = status; }
    public void setNote(String note) {
        this.note = note == null ? null : (note.length() > 255 ? note.substring(0, 255) : note);
    }
    public void setWalletTransactionId(Long walletTransactionId) { this.walletTransactionId = walletTransactionId; }
    public void setRefundTransactionId(Long refundTransactionId) { this.refundTransactionId = refundTransactionId; }
    public void setDeliveryName(String deliveryName) { this.deliveryName = deliveryName; }
    public void setDeliveryPhone(String deliveryPhone) { this.deliveryPhone = deliveryPhone; }
    public void setDeliveryAddress(String deliveryAddress) { this.deliveryAddress = deliveryAddress; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
