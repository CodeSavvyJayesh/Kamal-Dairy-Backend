package com.kamaldairy.kamal_dairy_backend.controller;

import com.kamaldairy.kamal_dairy_backend.dto.PlaceOrderRequest;
import com.kamaldairy.kamal_dairy_backend.model.Order;
import com.kamaldairy.kamal_dairy_backend.service.OrderService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Places an order. Requires verified proof of payment in the body -
     * a bare POST is now rejected.
     */
    @PostMapping("/place")
    public Order placeOrder(
            @RequestBody PlaceOrderRequest request,
            Authentication authentication
    ) {
        return orderService.placeOrder(authentication.getName(), request);
    }

    @GetMapping("/my-orders")
    public List<Order> getMyOrders(Authentication authentication) {
        return orderService.getUserOrders(authentication.getName());
    }
}
