package com.kamaldairy.kamal_dairy_backend.model;

public class Product {
    private int id;
    private String name;
    private double price;
    private String category;
    private String imageUrl;

    public Product(int id,String name,double price,String category,String imageUrl)
    {
        this.id=id;
        this.name=name;
        this.price=price;
        this.category=category;
        this.imageUrl=imageUrl;
    }
    // now we need getters
    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public double getPrice() {
        return price;
    }

    public String getCategory() {
        return category;
    }

    public String getImageUrl() {
        return imageUrl;
    }

}
