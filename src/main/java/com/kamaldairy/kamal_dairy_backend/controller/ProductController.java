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

        ),
                new Product(
                        10,
                        "FreshFarm Double Toned Milk 1L",
                        52,
                        "milk",
                        "/images/milk/FreshFarm Double Toned Milk 1L.jpg"
                ),
                new Product(
                        11,
                        "HealthyCow Protein Rich Milk 1L",
                        75,
                        "milk",
                        "/images/milk/HealthyCow Protein Rich Milk 1L.jpg"
                ),
                new Product(
                        12,
                        "CreamLand Farm Fresh Milk 500ml",
                        32,
                        "milk",
                        "/images/milk/CreamLand Farm Fresh Milk 500ml.jpg"
                ),
                new Product(
                        13,
                        "PureGold Gold Milk 1L",
                        65,
                        "milk",
                        "/images/milk/PureGold Gold Milk 1L.jpg"
                ),
                new Product(
                        13,
                        "Kamal Dairy A2 Cow Milk 1L",
                        90,
                        "milk",
                        "/images/milk/Kamal Dairy A2 Cow Milk 1L.jpg"
                ),
                new Product(
                        14,
                        "MilkyWay Standard Milk 1L",
                        60,
                        "milk",
                        "/images/milk/MilkyWay Standard Milk 1L.jpg"
                ),
                new Product(
                        15,
                        "DailyFresh Pasteurized Milk 500ml",
                        29,
                        "milk",
                        "/images/milk/DailyFresh Pasteurized Milk 500ml.jpg"
                ),
                new Product(
                        16,
                        "HealthyCow High Calcium Milk 1L",
                        72,
                        "milk",
                        "/images/milk/HealthyCow High Calcium Milk 1L.jpg"
                ),
                new Product(
                        17,
                        "FreshFarm Jersey Cow Milk 1L",
                        67,
                        "milk",
                        "/images/milk/FreshFarm Jersey Cow Milk 1L.jpg"
                ),
                new Product(
                        18,
                        "PureGold Premium Milk 500ml",
                        35,
                        "milk",
                        "/images/milk/PureGold Premium Milk 500ml.jpg"
                ),
                new Product(
                        19,
                        "CreamLand Ultra-Filtered Milk 1L",
                        78,
                        "milk",
                        "/images/milk/CreamLand Ultra-Filtered Milk 1L.jpg"
                ),
                new Product(
                        20,
                        "DailyFresh Vitamin Milk 1L",
                        69,
                        "milk",
                        "/images/milk/DailyFresh Vitamin Milk 1L.jpg"
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