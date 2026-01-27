package com.kamaldairy.kamal_dairy_backend.repository;

import com.kamaldairy.kamal_dairy_backend.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product,Integer> {

    List<Product> findByCategory(String category);
    List<Product> findTop3ByIsTrendingTrue();
}
