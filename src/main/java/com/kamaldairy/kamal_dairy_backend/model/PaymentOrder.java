package com.kamaldairy.kamal_dairy_backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Server-side record of every Razorpay order this backend creates.
 *
 * This is the anchor for payment verification. It remembers WHO the order was
 * created for and HOW MUCH the server decided it should cost, so that when the
 * browser comes back claiming "payment done", we can check the claim against
 * something the browser could not tamper with.
 *
 * status also gives us replay protection: a payment reference can be consumed
 * exactly once.
 */
@Entity
@Table(name = "payment_orders")
public class PaymentOrder {

    public static final String STATUS_CREATED = "CREATED";
    public static final String STATUS_PAID = "PAID";

    /** What the money is for. A top-up payment can never be redeemed as a cart order, or vice versa. */
    public static final String PURPOSE_ORDER = "ORDER";
    public static final String PURPOSE_WALLET_TOPUP = "WALLET_TOPUP";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "razorpay_order_id", nullable = false, unique = true)
    private String razorpayOrderId;

    @Column(name = "user_email", nullable = false)
    private String userEmail;

    /** Amount in paise, exactly as sent to Razorpay. Integer, never a double. */
    @Column(name = "amount_paise", nullable = false)
    private long amountPaise;

    @Column(nullable = false)
    private String status;

    @Column(name = "razorpay_payment_id")
    private String razorpayPaymentId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    /** Nullable so rows created before this column existed still load; null means ORDER. */
    @Column(length = 20)
    private String purpose;

    public PaymentOrder() {}

    public PaymentOrder(String razorpayOrderId, String userEmail, long amountPaise) {
        this(razorpayOrderId, userEmail, amountPaise, PURPOSE_ORDER);
    }

    public PaymentOrder(String razorpayOrderId, String userEmail, long amountPaise, String purpose) {
        this.razorpayOrderId = razorpayOrderId;
        this.userEmail = userEmail;
        this.amountPaise = amountPaise;
        this.status = STATUS_CREATED;
        this.createdAt = LocalDateTime.now();
        this.purpose = purpose;
    }

    public boolean isWalletTopup() {
        return PURPOSE_WALLET_TOPUP.equals(purpose);
    }

    public boolean isCartOrder() {
        return purpose == null || PURPOSE_ORDER.equals(purpose);
    }

    public void markPaid(String razorpayPaymentId) {
        this.razorpayPaymentId = razorpayPaymentId;
        this.status = STATUS_PAID;
        this.paidAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getRazorpayOrderId() { return razorpayOrderId; }
    public String getUserEmail() { return userEmail; }
    public long getAmountPaise() { return amountPaise; }
    public String getStatus() { return status; }
    public String getRazorpayPaymentId() { return razorpayPaymentId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public String getPurpose() { return purpose; }

    public void setRazorpayOrderId(String razorpayOrderId) { this.razorpayOrderId = razorpayOrderId; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }
    public void setAmountPaise(long amountPaise) { this.amountPaise = amountPaise; }
    public void setStatus(String status) { this.status = status; }
}
