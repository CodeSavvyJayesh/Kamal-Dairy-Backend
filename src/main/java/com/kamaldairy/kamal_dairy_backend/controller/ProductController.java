package com.kamaldairy.kamal_dairy_backend.controller;

import com.kamaldairy.kamal_dairy_backend.model.Product;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@CrossOrigin(origins = "http://localhost:5173")
public class ProductController {

    @GetMapping("/api/products/{category}")
    public List<Product> getProductsByCategory(@PathVariable String category) {

        List<Product> allProducts = List.of(
                new Product(
                        1,
                        "Kamal Dairy Full Cream Milk 1L",
                        62,
                        "milk",
                        "/images/milk/Kamal Dairy Full Cream Milk 1L.jpg"
                ),
                new Product(
                        2,
                        "FreshFarm Cow Milk 1L",
                        58,
                        "milk",
                        "/images/milk/FreshFarm Cow Milk 1L.jpg"
                ),
                new Product(
                        3,
                        "Kamal Dairy Fresh Paneer 200g",
                        85,
                        "paneer",
                        "/images/paneer/Kamal Dairy Fresh Paneer 200g.jpg"
                ),
        new Product(
                4,
                "DailyFresh Toned Milk 500ml",
                30,
                "milk",
                "/images/milk/DailyFresh Toned Milk 500ml.jpg"
        ),
                new Product(
                        5,
                        "FreshFarm Cow Milk 1L",
                        58,
                        "milk",
                        "/images/milk/FreshFarm Cow Milk 1L.jpg"
                ),
                new Product(
                        6,
                        "HealthyCow Organic Milk 1L",
                        70,
                        "milk",
                        "/images/milk/HealthyCow Organic Milk 1L.jpg"
                ),
                new Product(
                        7,
                        "CreamLand Skimmed Milk 1L",
                        55,
                        "milk",
                        "/images/milk/CreamLand Skimmed Milk 1L.jpg"
                ),
                new Product(
                        8,
                        "PureGold Buffalo Milk 1L",
                        68,
                        "milk",
                        "/images/milk/PureGold Buffalo Milk 1L.jpg"
                ),
        new Product(
                9,
                "MilkyWay Low Fat Milk 500ml",
                28,
                "milk",
                "/images/milk/MilkyWay Low Fat Milk 500ml.jpg"
        )


        );

        // ✅ FILTER BASED ON CATEGORY FROM URL
        return allProducts.stream()
                .filter(product ->
                        product.getCategory().equalsIgnoreCase(category)
                )
                .toList();
    }
}