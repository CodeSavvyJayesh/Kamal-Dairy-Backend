package com.kamaldairy.kamal_dairy_backend.controller;

import com.kamaldairy.kamal_dairy_backend.model.Order;
import com.kamaldairy.kamal_dairy_backend.service.OrderService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@CrossOrigin(origins = "*")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    // 🔥 PLACE ORDER
    @PostMapping("/place")
    public Order placeOrder(Authentication authentication) {

        String userEmail = authentication.getName();
        System.out.println("ORDER SAVED FOR: " + userEmail); // debug

        return orderService.placeOrder(userEmail);
    }

    // 🔥 GET MY ORDERS
    @GetMapping("/my-orders")
    public List<Order> getMyOrders(Authentication authentication) {

        String userEmail = authentication.getName();
        System.out.println("FETCH EMAIL: " + userEmail); // debug

        List<Order> orders = orderService.getUserOrders(userEmail);
        System.out.println("Orders found : " + orders.size());

        return orders;
    }
}