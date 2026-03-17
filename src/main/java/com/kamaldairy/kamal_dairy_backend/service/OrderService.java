package com.kamaldairy.kamal_dairy_backend.service;

import com.kamaldairy.kamal_dairy_backend.model.CartItem;
import com.kamaldairy.kamal_dairy_backend.model.Order;
import com.kamaldairy.kamal_dairy_backend.model.OrderItem;
import com.kamaldairy.kamal_dairy_backend.repository.CartRepository;
import com.kamaldairy.kamal_dairy_backend.repository.OrderItemRepository;
import com.kamaldairy.kamal_dairy_backend.repository.OrderRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class OrderService {

    private final CartRepository cartRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    public OrderService(
            CartRepository cartRepository,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository
    ) {
        this.cartRepository = cartRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
    }

    @Transactional
    public Order placeOrder(String userEmail) {

        // 1️ Fetch cart items
        List<CartItem> cartItems = cartRepository.findByUserEmail(userEmail);

        if (cartItems.isEmpty()) {
            throw new RuntimeException("Cart is empty. Cannot place order.");
        }

        // 2️ Create order
        Order order = new Order();
        order.setUserEmail(userEmail);

        double totalAmount = 0.0;

        List<OrderItem> orderItems = new ArrayList<>();

        // 3️ Convert cart items → order items
        for (CartItem cartItem : cartItems) {

            OrderItem orderItem = new OrderItem();

            orderItem.setProductId(cartItem.getProductId());
            orderItem.setProductName(cartItem.getProductName());
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setPrice(cartItem.getPrice());

            orderItem.setOrder(order);

            orderItems.add(orderItem);

            totalAmount += cartItem.getPrice() * cartItem.getQuantity();
        }

        // 4 Attach items to order
        order.setItems(orderItems);
        order.setTotalAmount(totalAmount);

        // 5️ Save order (cascade saves orderItems)
        Order savedOrder = orderRepository.save(order);

        // 6⃣ Clear cart
        cartRepository.deleteAll(cartItems);

        return savedOrder;
    }
}