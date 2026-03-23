package com.kamaldairy.kamal_dairy_backend.service;

import com.kamaldairy.kamal_dairy_backend.model.CartItem;
import com.kamaldairy.kamal_dairy_backend.model.Order;
import com.kamaldairy.kamal_dairy_backend.model.OrderItem;
import com.kamaldairy.kamal_dairy_backend.repository.CartRepository;
import com.kamaldairy.kamal_dairy_backend.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class OrderService {

    private final CartRepository cartRepository;
    private final OrderRepository orderRepository;

    public OrderService(
            CartRepository cartRepository,
            OrderRepository orderRepository
    ) {
        this.cartRepository = cartRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public Order placeOrder(String userEmail) {

        List<CartItem> cartItems = cartRepository.findByUserEmail(userEmail);

        if (cartItems.isEmpty()) {
            throw new RuntimeException("Cart is empty");
        }

        Order order = new Order();
        order.setUserEmail(userEmail);

        double totalAmount = 0.0;
        List<OrderItem> orderItems = new ArrayList<>();

        for (CartItem cartItem : cartItems) {

            OrderItem item = new OrderItem();
            item.setProductId(cartItem.getProductId());
            item.setProductName(cartItem.getProductName());
            item.setQuantity(cartItem.getQuantity());
            item.setPrice(cartItem.getPrice());
            item.setOrder(order);

            orderItems.add(item);

            totalAmount += cartItem.getPrice() * cartItem.getQuantity();
        }

        order.setItems(orderItems);
        order.setTotalAmount(totalAmount);

        Order saved = orderRepository.save(order);

        cartRepository.deleteAll(cartItems);

        return saved;
    }

    public List<Order> getUserOrders(String userEmail) {
        return orderRepository.findByUserEmail(userEmail);
    }
}