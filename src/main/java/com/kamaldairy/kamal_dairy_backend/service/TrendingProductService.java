package com.kamaldairy.kamal_dairy_backend.service;

import com.kamaldairy.kamal_dairy_backend.model.Product;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Service
public class TrendingProductService {
    // here basically we have to write the business logic
       private final List<Product>
         trendingProducts = List.of(
                new Product(
                        1,
                        "Kamal Dairy Full Cream Milk 1L",
                        62,
                        "milk",
                        "/images/milk/Kamal Dairy Full Cream Milk 1L.jpg"
                ),
                new Product(
                        2,
                        "Kamal Dairy Fresh Paneer 200g",
                        85,
                        "paneer",
                        "/images/paneer/Kamal Dairy Fresh Paneer 200g.jpg"
                ),
                new Product(
                        3,
                        "Kamal Dairy Salted Butter 100g",
                        52,
                        "butter",
                        "/images/butter/Kamal Dairy Salted Butter 100g.jpg"

                )
        );

       public List<Product> getTrendingProducts()
       {
           return trendingProducts;
       }

}
