package com.kamaldairy.kamal_dairy_backend.model;


import jakarta.persistence.*;

@Entity
@Table(name = "cart_items")
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String userEmail;
    private Integer productId;
    private String productName;
    private double price;
    private int quantity;

    /* Filled in when the cart is read, never stored. */
    @Transient
    private Integer stock;
    @Transient
    private String imageUrl;

    public CartItem(){}
    public CartItem(String userEmail,Integer productId,String productName,double price,int quantity)
    {
        this.userEmail = userEmail;
        this.productId = productId;
        this.productName  = productName;
        this.price = price;
        this.quantity =  quantity;

    }
    // make the getters and setters
    public Integer getId(){
        return id;
    }
    public String getUserEmail(){
        return userEmail;
    }
    public Integer getProductId(){
        return productId;
    }
    public String getProductName(){
        return productName;
    }
    public double getPrice(){
        return price;
    }
    public int getQuantity(){
        return quantity;
    }

    // setters
    public void setUserEmail(String userEmail)
    {
        this.userEmail = userEmail;
    }
    public void setProductId(Integer productId)
    {
        this.productId = productId;
    }
    public void setProductName(String productName)
    {
        this.productName = productName;
    }
    public void setPrice(double price)
    {
        this.price = price;
    }
    public void setQuantity(int quantity)
    {
        this.quantity = quantity;
    }

    /** Units left on the shelf, or null when the product is not stock-tracked. */
    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
}
