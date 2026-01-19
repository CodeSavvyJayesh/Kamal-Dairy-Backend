package com.kamaldairy.kamal_dairy_backend.controller;

import com.kamaldairy.kamal_dairy_backend.model.Product;
import com.kamaldairy.kamal_dairy_backend.service.ProductService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@CrossOrigin(origins = "http://localhost:5173")
@RequestMapping("/api/products")
public class ProductController {

     private final ProductService productService;

     public ProductController(ProductService productService) {
          this.productService = productService;

     }
     @GetMapping("/{category}")
     public List<Product> getProductByCategory(@PathVariable String category)
     {
          return productService.getProductsByCategory(category);
    }
}