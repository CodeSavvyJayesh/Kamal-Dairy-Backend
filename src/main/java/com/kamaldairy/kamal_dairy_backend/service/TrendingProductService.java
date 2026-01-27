package com.kamaldairy.kamal_dairy_backend.service;

import com.kamaldairy.kamal_dairy_backend.controller.TrendingProductController;
import com.kamaldairy.kamal_dairy_backend.model.Product;
import com.kamaldairy.kamal_dairy_backend.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TrendingProductService {
      private final ProductRepository productRepository;
      public TrendingProductService(ProductRepository productRepository)
      {
           this.productRepository = productRepository;
      }
      public List<Product> getTrendingProducts(){
          return productRepository.findTop3ByIsTrendingTrue();
      }

}
