package com.kamaldairy.kamal_dairy_backend.controller;

import com.kamaldairy.kamal_dairy_backend.model.Order;
import com.kamaldairy.kamal_dairy_backend.service.OrderService;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    // 🔥 Only USER can create order
    @PreAuthorize("hasRole('USER')")
    @PostMapping
    public Order createOrder(
            @RequestParam String productName,
            @RequestParam double price,
            Principal principal
    ) {
        String userEmail = principal.getName();
        return orderService.createOrder(productName, price, userEmail);
    }

    // 🔥 Any authenticated user can view their own orders
    @GetMapping
    public List<Order> getMyOrders(Principal principal) {
        String userEmail = principal.getName();
        return orderService.getUserOrders(userEmail);
    }
}