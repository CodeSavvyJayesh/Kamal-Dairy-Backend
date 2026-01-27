package com.kamaldairy.kamal_dairy_backend.controller;

import com.kamaldairy.kamal_dairy_backend.model.Product;
import com.kamaldairy.kamal_dairy_backend.service.TrendingProductService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/trending-products")
@CrossOrigin(origins = "http://localhost:5173")
public class TrendingProductController {

    private final TrendingProductService trendingProductService;

    public TrendingProductController(TrendingProductService trendingProductService) {
        this.trendingProductService = trendingProductService;
    }

    @GetMapping
    public List<Product> getTrendingProducts() {
        return trendingProductService.getTrendingProducts();
    }
}