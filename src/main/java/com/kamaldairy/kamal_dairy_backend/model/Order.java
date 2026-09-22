package com.kamaldairy.kamal_dairy_backend.model;

import com.kamaldairy.kamal_dairy_backend.dto.DeliveryAddress;
import com.kamaldairy.kamal_dairy_backend.util.Money;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String userEmail;

    private double totalAmount;

    /** Razorpay references, kept for audit and reconciliation. */
    @Column(name = "razorpay_order_id")
    private String razorpayOrderId;

    @Column(name = "razorpay_payment_id")
    private String razorpayPaymentId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public static final String PAY_RAZORPAY = "RAZORPAY";
    public static final String PAY_WALLET = "WALLET";

    /** RAZORPAY or WALLET. Null on orders placed before wallet payments existed (all Razorpay). */
    @Column(name = "payment_method", length = 12)
    private String paymentMethod;

    /*
     * Where the order goes. Validated before payment is taken. Null only on
     * orders placed before addresses were saved.
     */
    @Column(name = "delivery_name", length = 80)
    private String deliveryName;

    @Column(name = "delivery_phone", length = 15)
    private String deliveryPhone;

    @Column(name = "delivery_address", length = 255)
    private String deliveryAddress;

    @Column(name = "delivery_city", length = 60)
    private String deliveryCity;

    @Column(name = "delivery_pincode", length = 6)
    private String deliveryPincode;

    /*
     * Lifecycle. Null only on orders placed before the lifecycle existed;
     * those read as DELIVERED and can no longer change.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private OrderStatus status;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "out_for_delivery_at")
    private LocalDateTime outForDeliveryAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancel_reason", length = 200)
    private String cancelReason;

    /** CUSTOMER or ADMIN */
    @Column(name = "cancelled_by", length = 10)
    private String cancelledBy;

    @Column(name = "refunded_paise")
    private Long refundedPaise;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items;

    public Order() {}

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Integer getId() { return id; }

    public String getUserEmail() { return userEmail; }

    public double getTotalAmount() { return totalAmount; }

    public List<OrderItem> getItems() { return items; }

    public String getRazorpayOrderId() { return razorpayOrderId; }

    public String getRazorpayPaymentId() { return razorpayPaymentId; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public String getPaymentMethod() { return paymentMethod == null ? PAY_RAZORPAY : paymentMethod; }

    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getDeliveryName() { return deliveryName; }

    public String getDeliveryPhone() { return deliveryPhone; }

    public String getDeliveryAddress() { return deliveryAddress; }

    public String getDeliveryCity() { return deliveryCity; }

    public String getDeliveryPincode() { return deliveryPincode; }

    // ------------------------------------------------------------ lifecycle

    public OrderStatus getStatus() { return status == null ? OrderStatus.DELIVERED : status; }

    public String getStatusLabel() { return getStatus().label(); }

    /** The customer may cancel only until the dairy confirms. */
    public boolean isCancellable() { return status == OrderStatus.PLACED; }

    public LocalDateTime getConfirmedAt() { return confirmedAt; }

    public LocalDateTime getOutForDeliveryAt() { return outForDeliveryAt; }

    public LocalDateTime getDeliveredAt() { return deliveredAt; }

    public LocalDateTime getCancelledAt() { return cancelledAt; }

    public String getCancelReason() { return cancelReason; }

    public String getCancelledBy() { return cancelledBy; }

    /** Rupees returned to the wallet on cancellation, or null. */
    public BigDecimal getRefundedAmount() { return refundedPaise == null ? null : Money.toRupees(refundedPaise); }

    public void setStatus(OrderStatus status) { this.status = status; }

    /** Forward move. Validation is the caller's job (OrderLifecycleService). */
    public void moveTo(OrderStatus next, LocalDateTime at) {
        this.status = next;
        switch (next) {
            case CONFIRMED -> confirmedAt = at;
            case OUT_FOR_DELIVERY -> outForDeliveryAt = at;
            case DELIVERED -> deliveredAt = at;
            default -> { }
        }
    }

    public void cancel(LocalDateTime at, String by, String reason, long refundPaise) {
        this.status = OrderStatus.CANCELLED;
        this.cancelledAt = at;
        this.cancelledBy = by;
        this.cancelReason = reason;
        this.refundedPaise = refundPaise;
    }

    /** Copies an already-validated address onto the order. */
    public void deliverTo(DeliveryAddress a) {
        this.deliveryName = a.name();
        this.deliveryPhone = a.phone();
        this.deliveryAddress = a.address();
        this.deliveryCity = a.city();
        this.deliveryPincode = a.pincode();
    }

    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }

    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }

    public void setItems(List<OrderItem> items) { this.items = items; }

    public void setRazorpayOrderId(String razorpayOrderId) { this.razorpayOrderId = razorpayOrderId; }

    public void setRazorpayPaymentId(String razorpayPaymentId) { this.razorpayPaymentId = razorpayPaymentId; }

    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
