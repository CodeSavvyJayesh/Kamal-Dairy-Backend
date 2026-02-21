package com.kamaldairy.kamal_dairy_backend.service;

import com.kamaldairy.kamal_dairy_backend.model.Order;
import com.kamaldairy.kamal_dairy_backend.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OrderService {
    // this class will consist the business logic for the orders

    private final OrderRepository orderRepository;
    public OrderService(OrderRepository orderRepository)
    {
        this.orderRepository = orderRepository;
    }
    // create order
    public Order createOrder(String productName, double price, String userEmail)
    {
        Order order = new Order(productName,price,userEmail);
        return orderRepository.save(order);
    }
    // display order
    public List<Order> getUserOrders(String userEmail)
    {
         return orderRepository.findByUserEmail(userEmail);
    }
}
