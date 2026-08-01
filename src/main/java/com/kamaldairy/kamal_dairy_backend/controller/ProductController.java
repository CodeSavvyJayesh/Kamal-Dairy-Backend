package com.kamaldairy.kamal_dairy_backend.controller;

import com.kamaldairy.kamal_dairy_backend.model.Product;
import com.kamaldairy.kamal_dairy_backend.service.ProductService;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@CrossOrigin(origins = "*")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    //  PUBLIC - Anyone can see all products
    /*
    @GetMapping
    public List<Product> getAllProducts() {
        return productService.getAllProducts();
    } */
    // here we have to think about the pagination
    @GetMapping
    public Page<Product> getAllProducts(@RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size)
    {
        return productService.getAllProducts(page,size);
    }

    //  PUBLIC - Category-wise products
    @GetMapping("/{category}")
    public List<Product> getProductsByCategory(@PathVariable String category) {
        return productService.getProductsByCategory(category);
    }

    // ADMIN ONLY - Add product
    @PreAuthorize("hasRole('USER')")
    @PostMapping
    public Product addProduct(@RequestBody Product product) {
        return productService.saveProduct(product);
    }

    // ADMIN ONLY - Update product
    @PreAuthorize("hasRole('USER')")
    @PutMapping("/{id}")
    public Product updateProduct(@PathVariable Integer id,
                                 @RequestBody Product updatedProduct) {
        return productService.updateProduct(id, updatedProduct);
    }

    // ADMIN ONLY - Delete product
    @PreAuthorize("hasRole('USER')")
    @DeleteMapping("/{id}")
    public String deleteProduct(@PathVariable Integer id) {
        productService.deleteProduct(id);
        return "Product deleted successfully";
    }
}



// now we have to think about the pagination in this project basically we are having 52k+ products in the
// inventory so if I hit getallproducts i will receive all the products in one page only
// solution for that we have to use multiple pages : this is what pagination is
// so basically with pagination in first page 20 , second 20 , third 20 and so on
// without pagination : 52k * 1kb ( product size)  = 52MB  backend sends 52MB
// with pagination : 20*1 kb = backend sends only 20kb huge difference

// api would be much faster.... we can see we are facing issue on the admin dashboard as we are listing
// all the products in one page which is basically not working
// with pagination