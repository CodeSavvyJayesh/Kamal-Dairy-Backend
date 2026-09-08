package com.kamaldairy.kamal_dairy_backend.model;

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

    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }

    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }

    public void setItems(List<OrderItem> items) { this.items = items; }

    public void setRazorpayOrderId(String razorpayOrderId) { this.razorpayOrderId = razorpayOrderId; }

    public void setRazorpayPaymentId(String razorpayPaymentId) { this.razorpayPaymentId = razorpayPaymentId; }

    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
