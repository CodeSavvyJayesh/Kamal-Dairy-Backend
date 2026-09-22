package com.kamaldairy.kamal_dairy_backend.service;

import com.kamaldairy.kamal_dairy_backend.exception.ApiException;
import com.kamaldairy.kamal_dairy_backend.exception.ResourceNotFoundException;
import com.kamaldairy.kamal_dairy_backend.model.Product;
import com.kamaldairy.kamal_dairy_backend.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;


import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    // 🔹 Constructor Injection
    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    // 🔹 Get all products (PUBLIC)
    // here we are returning all the products which is not wrong but for 50k+ products its not recommended
    // better we have to use pagination here
    /*public List<Product> getAllProducts() {
        return productRepository.findAll();
    }*/
    // we can replace the above with something like this :
    public Page<Product>getAllProducts(int page, int size)
    {
        Pageable pageable = PageRequest.of(page,size);

        return productRepository.findAll(pageable);
    }

    // 🔹 Get products by category (PUBLIC)
    public List<Product> getProductsByCategory(String category) {
        return productRepository.findByCategory(category);
    }

    // 🔐 ADMIN - Save product
    public Product saveProduct(Product product) {
        // Starting stock may be set on create. Later changes go through
        // /api/admin/products/{id}/stock so a stale form can never overwrite
        // stock that orders have already taken.
        Integer stock = product.getStock();
        if (stock != null && (stock < 0 || stock > StockService.MAX_STOCK)) {
            throw new ApiException("Stock must be between 0 and " + StockService.MAX_STOCK + ".",
                    HttpStatus.BAD_REQUEST);
        }
        return productRepository.save(product);
    }

    // 🔐 ADMIN - Update product
    public Product updateProduct(Integer id, Product updatedProduct) {

        Product existingProduct = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product"));

        // Stock is deliberately not copied here - see saveProduct.

        existingProduct.setName(updatedProduct.getName());
        existingProduct.setPrice(updatedProduct.getPrice());
        existingProduct.setCategory(updatedProduct.getCategory());
        existingProduct.setImageUrl(updatedProduct.getImageUrl());

        return productRepository.save(existingProduct);
    }

    // 🔐 ADMIN - Delete product
    public void deleteProduct(Integer id) {

        if (!productRepository.existsById(id)) {
            throw new ResourceNotFoundException("Product");
        }

        productRepository.deleteById(id);
    }
}