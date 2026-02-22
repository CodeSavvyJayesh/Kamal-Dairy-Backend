package com.kamaldairy.kamal_dairy_backend.service;

import com.kamaldairy.kamal_dairy_backend.model.Product;
import com.kamaldairy.kamal_dairy_backend.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    // 🔹 Constructor Injection
    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    // 🔹 Get all products (PUBLIC)
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    // 🔹 Get products by category (PUBLIC)
    public List<Product> getProductsByCategory(String category) {
        return productRepository.findByCategory(category);
    }

    // 🔐 ADMIN - Save product
    public Product saveProduct(Product product) {
        return productRepository.save(product);
    }

    // 🔐 ADMIN - Update product
    public Product updateProduct(Integer id, Product updatedProduct) {

        Product existingProduct = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        existingProduct.setName(updatedProduct.getName());
        existingProduct.setPrice(updatedProduct.getPrice());
        existingProduct.setCategory(updatedProduct.getCategory());
        existingProduct.setImageUrl(updatedProduct.getImageUrl());

        return productRepository.save(existingProduct);
    }

    // 🔐 ADMIN - Delete product
    public void deleteProduct(Integer id) {

        if (!productRepository.existsById(id)) {
            throw new RuntimeException("Product not found");
        }

        productRepository.deleteById(id);
    }
}