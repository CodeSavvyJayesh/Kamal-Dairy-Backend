package com.kamaldairy.kamal_dairy_backend.model;

import jakarta.persistence.*;

@Entity
@Table(name="orders")
public class Order {
    // this is Order Entity
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String productName;
    private double price;
    // v.imp as JWT gives us email we link order to that email

    private String userEmail;
    public Order(){
    }
    public Order(String productName,double price, String userEmail)
    {
        this.productName = productName;
        this.price = price;
        this.userEmail = userEmail;
    }
    public Integer getId(){
        return id;
    }
    public String getProductName(){
        return productName;
    }
    public double getPrice(){
        return price;
    }
    public String getUserEmail(){
        return userEmail;
    }
    public void setProductName(String productName)
    {
        this.productName = productName;
    }
    public void setPrice(double price)
    {
        this.price = price;
    }
    public void setUserEmail(String userEmail)
    {
        this.userEmail = userEmail;
    }

}
