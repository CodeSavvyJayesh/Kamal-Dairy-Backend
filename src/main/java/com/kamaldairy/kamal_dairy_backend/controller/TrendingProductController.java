package com.kamaldairy.kamal_dairy_backend.controller;

import com.kamaldairy.kamal_dairy_backend.model.Product;
import com.kamaldairy.kamal_dairy_backend.service.ProductService;
import com.kamaldairy.kamal_dairy_backend.service.TrendingProductService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@CrossOrigin(origins = "http://localhost:5173")
public class TrendingProductController {

          private final TrendingProductService trendingProductService;

    public TrendingProductController(TrendingProductService trendingProductService) {
        this.trendingProductService = trendingProductService;
    }
      @GetMapping("/api/products")
      public List<Product> getTrendingProducts() {
        return trendingProductService.getTrendingProducts();
      }
}

