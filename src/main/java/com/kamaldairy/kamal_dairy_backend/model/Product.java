/*package com.kamaldairy.kamal_dairy_backend.model;

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
*/

// now we have to convert this product.java to entity

package com.kamaldairy.kamal_dairy_backend.model;

import jakarta.persistence.*;

@Entity
@Table(name = "products")
public class Product{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
    private String name;
    private double price;
    private String category;
    @Column(name= "image_url")
    private String imageUrl;
    private boolean isTrending;

    // required by JPA
    public Product(){

    }
    public Product(int id,String name,double price,String category,String imageUrl)
    {
         this.id = id;
         this.name = name;
         this.price = price;
         this.category = category;
         this.imageUrl = imageUrl;
    }

    // getters and setters
    public int getId(){
         return id;
    }
    public String getName(){
         return name;

    }
    public double getPrice(){
         return price;

    }
    public String getCategory(){
         return category;
    }
    public String getImageUrl(){
        return imageUrl;
    }

    public void setID(Integer id)
    {
         this.id = id;
    }
    public void setName(String name)
    {
        this.name = name;
    }
    public void setPrice(double price)
    {
        this.price = price;

    }
    public void setCategory(String category)
    {
        this.category = category;

    }
    public void setImageUrl(String imageUrl)
    {
        this.imageUrl = imageUrl;
    }
}
