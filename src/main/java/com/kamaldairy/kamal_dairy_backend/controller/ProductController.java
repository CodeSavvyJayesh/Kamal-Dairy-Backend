package com.kamaldairy.kamal_dairy_backend.controller;

import com.kamaldairy.kamal_dairy_backend.model.Product;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@CrossOrigin(origins = "http://locahost:5173")
public class ProductController {

    @GetMapping("/api/products")
    public List<Product> getProducts() {

        return List.of(
                new Product(
                        1,
                        "Kamal Dairy Full Cream Milk 1L",
                        62,
                        "milk",
                        "/images/milk/kamal_fullcream_1l.jpg"
                ),
                new Product(
                        2,
                        "Kamal Dairy Fresh Paneer 200g",
                        85,
                        "paneer",
                        "/images/paneer/kamal_fresh_200g.jpg"
                )
        );
    }
}
