package com.kamaldairy.kamal_dairy_backend.controller;

import com.kamaldairy.kamal_dairy_backend.model.Order;
import com.kamaldairy.kamal_dairy_backend.service.OrderService;
import org.apache.tomcat.util.net.openssl.ciphers.Authentication;
import org.springframework.web.bind.annotation.*;
import jakarta.security.auth.message.AuthException;
import java.security.Principal;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    // this class will handle the http request
    // this will also consist of concept of autowiring with the service class
    private final OrderService orderService;
    public OrderController(OrderService orderService)
    {
        this.orderService = orderService;
    }
    // create order JWT required
    @PostMapping
    public Order createOrder(@RequestParam String productName, @RequestParam double price,
                             Principal principal)
    {
         String userEmail = principal.getName();
         return orderService.createOrder(productName,price,userEmail);


    }
    // get logged in users order
    @GetMapping
    public List<Order> getMyOrders(Principal principal)
    {
        String userEmail = principal.getName();
        return orderService.getUserOrders(userEmail);
    }
}
