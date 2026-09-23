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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import org.hibernate.annotations.DynamicUpdate;

@Entity
// Only changed columns are written. An admin editing the name or price can
// then never overwrite stock or rating that orders and reviews changed meanwhile.
@DynamicUpdate
// here we can see that we are cannot use the lombok annotations just because we have no added that dependence
// product name is mapping with the products
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

    /**
     * Units on the shelf for cart orders. null = not tracked, always
     * available. Only StockService changes it during checkout, under a row lock.
     */
    @Column(name = "stock")
    private Integer stock;

    /**
     * Published reviews: how many, and the sum of their stars. Kept in step by
     * ReviewService under the product's row lock. Read-only over the API.
     */
    @Column(name = "rating_count")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Integer ratingCount;

    @Column(name = "rating_total")
    @JsonIgnore
    private Integer ratingTotal;

    /**
     * HSN code for the invoice. Dairy goods sit in chapter 04 - for example
     * 0401 for fresh milk, 0406 for paneer, 0405 for butter and ghee. Blank is
     * allowed: the invoice then simply leaves the column empty.
     */
    @Column(name = "hsn_code", length = 12)
    private String hsnCode;

    /**
     * GST rate for this product as a whole percentage, and the shelf price is
     * treated as INCLUDING it. Left to the admin rather than hardcoded: rates
     * differ by product and change in council meetings, and only the dairy
     * knows which slab its packaging puts each item in. 0 (the default) means
     * exempt or nil-rated, which is where loose fresh milk sits.
     */
    @Column(name = "gst_rate_percent")
    private Integer gstRatePercent;

    // required by JPA
    // no args constructor
    public Product(){

    }
    // all args constructor
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

    public Integer getStock()
    {
        return stock;
    }

    public void setStock(Integer stock)
    {
        this.stock = stock;
    }

    public int getRatingCount()
    {
        return ratingCount == null ? 0 : ratingCount;
    }

    /** Average stars to one decimal, or null when there are no reviews yet. */
    public Double getRatingAverage()
    {
        int count = getRatingCount();
        if (count <= 0) return null;
        return Math.round(10.0 * (ratingTotal == null ? 0 : ratingTotal) / count) / 10.0;
    }

    public String getHsnCode()
    {
        return hsnCode;
    }

    public void setHsnCode(String hsnCode)
    {
        this.hsnCode = hsnCode == null || hsnCode.isBlank() ? null : hsnCode.trim();
    }

    /** 0 when not set, so older products simply invoice as nil-rated. */
    public int getGstRatePercent()
    {
        return gstRatePercent == null ? 0 : gstRatePercent;
    }

    public void setGstRatePercent(Integer gstRatePercent)
    {
        if (gstRatePercent != null && (gstRatePercent < 0 || gstRatePercent > 100)) {
            throw new IllegalArgumentException("GST rate must be between 0 and 100.");
        }
        this.gstRatePercent = gstRatePercent;
    }

    /** Only ReviewService calls this, with the product row locked. */
    public void applyRatingChange(int countDelta, int totalDelta)
    {
        this.ratingCount = Math.max(0, getRatingCount() + countDelta);
        this.ratingTotal = this.ratingCount == 0 ? 0 : Math.max(0, (ratingTotal == null ? 0 : ratingTotal) + totalDelta);
    }
}
