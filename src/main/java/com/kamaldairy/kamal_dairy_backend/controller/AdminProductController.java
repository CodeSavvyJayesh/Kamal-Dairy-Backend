package com.kamaldairy.kamal_dairy_backend.controller;

import com.kamaldairy.kamal_dairy_backend.dto.RestockRequest;
import com.kamaldairy.kamal_dairy_backend.dto.StockRequest;
import com.kamaldairy.kamal_dairy_backend.model.Product;
import com.kamaldairy.kamal_dairy_backend.service.StockService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Shelf stock. ADMIN only - enforced here and again in SecurityConfig. */
@RestController
@RequestMapping("/api/admin/products")
@PreAuthorize("hasRole('ADMIN')")
public class AdminProductController {

    private final StockService stockService;

    public AdminProductController(StockService stockService) {
        this.stockService = stockService;
    }

    /** Tracked products with 5 or fewer left, emptiest first. */
    @GetMapping("/stock-alerts")
    public List<Product> stockAlerts() {
        return stockService.lowStock();
    }

    /** Set the exact count after a stock take. {"stock": null} stops tracking. */
    @PutMapping("/{id}/stock")
    public Product setStock(@PathVariable("id") Integer id, @RequestBody StockRequest request) {
        return stockService.setStock(id, request == null ? null : request.stock());
    }

    /** A delivery arrived: add units to what is already there. Safe while orders are coming in. */
    @PostMapping("/{id}/restock")
    public Product restock(@PathVariable("id") Integer id, @RequestBody RestockRequest request) {
        return stockService.addStock(id, request == null ? null : request.quantity());
    }
}
