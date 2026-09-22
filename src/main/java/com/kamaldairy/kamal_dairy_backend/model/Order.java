package com.kamaldairy.kamal_dairy_backend.model;

import com.kamaldairy.kamal_dairy_backend.dto.DeliveryAddress;
import jakarta.persistence.*;

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
