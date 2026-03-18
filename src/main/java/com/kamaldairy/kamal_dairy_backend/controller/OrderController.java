package com.kamaldairy.kamal_dairy_backend.controller;

import com.kamaldairy.kamal_dairy_backend.model.Order;
import com.kamaldairy.kamal_dairy_backend.service.OrderService;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@CrossOrigin(origins = "http://localhost:5173")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/place")
    public Order placeOrder(Authentication authentication) {

        String userEmail = authentication.getName();

        return orderService.placeOrder(userEmail);
    }

    // get my orders new API
    @GetMapping("/my-orders")
    public List<Order> getMyOrders(Authentication authentication)
    {
        String userEmail = authentication.getName();

        return orderService.getUserOrders(userEmail);
    }
}