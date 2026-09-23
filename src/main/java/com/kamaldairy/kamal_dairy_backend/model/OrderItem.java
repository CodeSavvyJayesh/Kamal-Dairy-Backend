package com.kamaldairy.kamal_dairy_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private Integer productId;
    private String productName;
    private int quantity;
    private double price;

    /**
     * HSN code and GST rate as they stood when the order was placed.
     *
     * Snapshotted for the same reason the name and price are: an invoice is a
     * record of one moment. If the dairy later moves a product to a different
     * slab, or the council changes a rate, reprinting an old invoice must still
     * show the tax that was actually charged - not today's.
     */
    @Column(name = "hsn_code", length = 12)
    private String hsnCode;

    @Column(name = "gst_rate_percent")
    private Integer gstRatePercent;

    @ManyToOne
    @JoinColumn(name = "order_id")
    @JsonIgnore
    private Order order;

    public OrderItem() {}

    public Integer getId() {
        return id;
    }

    public Integer getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public int getQuantity() {
        return quantity;
    }

    public double getPrice() {
        return price;
    }

    public String getHsnCode() {
        return hsnCode;
    }

    public void setHsnCode(String hsnCode) {
        this.hsnCode = hsnCode;
    }

    /** 0 on items ordered before GST rates were recorded: they invoice as nil-rated. */
    public int getGstRatePercent() {
        return gstRatePercent == null ? 0 : gstRatePercent;
    }

    public void setGstRatePercent(Integer gstRatePercent) {
        this.gstRatePercent = gstRatePercent;
    }

    public Order getOrder() {
        return order;
    }

    public void setProductId(Integer productId) {
        this.productId = productId;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public void setOrder(Order order) {
        this.order = order;
    }
}