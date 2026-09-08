package com.kamaldairy.kamal_dairy_backend.service;

import com.kamaldairy.kamal_dairy_backend.dto.PlaceOrderRequest;
import com.kamaldairy.kamal_dairy_backend.exception.ApiException;
import com.kamaldairy.kamal_dairy_backend.exception.PaymentVerificationException;
import com.kamaldairy.kamal_dairy_backend.exception.ResourceNotFoundException;
import com.kamaldairy.kamal_dairy_backend.model.*;
import com.kamaldairy.kamal_dairy_backend.repository.CartRepository;
import com.kamaldairy.kamal_dairy_backend.repository.OrderRepository;
import com.kamaldairy.kamal_dairy_backend.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final CartRepository cartRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final PaymentService paymentService;

    public OrderService(
            CartRepository cartRepository,
            OrderRepository orderRepository,
            ProductRepository productRepository,
            PaymentService paymentService
    ) {
        this.cartRepository = cartRepository;
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.paymentService = paymentService;
    }

    /**
     * Places an order ONLY against a payment we can prove is genuine.
     *
     * Five gates, in order:
     *   1. we have a record of this Razorpay order
     *   2. that record belongs to the caller
     *   3. the HMAC signature checks out against our Razorpay secret
     *   4. the payment has not already been used for another order
     *   5. the cart total still equals the amount that was actually charged
     *
     * The old version had none of these: it accepted a bare POST from anyone
     * holding a login token and created a free order.
     */
    @Transactional
    public Order placeOrder(String userEmail, PlaceOrderRequest request) {

        if (request == null) {
            throw new PaymentVerificationException("Payment details are required.");
        }

        // 1 + 2
        PaymentOrder paymentOrder = paymentService.requireOwnPaymentOrder(
                userEmail, request.getRazorpayOrderId());

        // 3
        paymentService.verifySignature(
                request.getRazorpayOrderId(),
                request.getRazorpayPaymentId(),
                request.getRazorpaySignature());

        // 4 - atomic, so a replay loses the race instead of duplicating an order
        if (!paymentService.consume(paymentOrder.getId(), request.getRazorpayPaymentId())) {
            throw new PaymentVerificationException(
                    "This payment has already been used for an order.");
        }

        List<CartItem> cartItems = cartRepository.findByUserEmail(userEmail);

        if (cartItems.isEmpty()) {
            throw new ApiException("Your cart is empty.", HttpStatus.BAD_REQUEST);
        }

        Order order = new Order();
        order.setUserEmail(userEmail);
        order.setRazorpayOrderId(request.getRazorpayOrderId());
        order.setRazorpayPaymentId(request.getRazorpayPaymentId());

        BigDecimal total = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();

        for (CartItem cartItem : cartItems) {

            // Prices are re-read from the products table, not trusted from the cart row.
            Product product = productRepository.findById(cartItem.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Product in your cart (id " + cartItem.getProductId() + ")"));

            OrderItem item = new OrderItem();
            item.setProductId(product.getId());
            item.setProductName(product.getName());
            item.setQuantity(cartItem.getQuantity());
            item.setPrice(product.getPrice());
            item.setOrder(order);

            orderItems.add(item);

            total = total.add(
                    BigDecimal.valueOf(product.getPrice())
                            .multiply(BigDecimal.valueOf(cartItem.getQuantity())));
        }

        long totalPaise = total.multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();

        // 5 - the customer must not be able to swell the cart after paying
        if (totalPaise != paymentOrder.getAmountPaise()) {
            log.warn("Amount mismatch for {}: charged {} paise but cart is now {} paise",
                    userEmail, paymentOrder.getAmountPaise(), totalPaise);
            throw new PaymentVerificationException(
                    "Your cart changed after payment was started. Nothing has been ordered - "
                    + "please contact support so we can refund or complete this payment.");
        }

        order.setItems(orderItems);
        order.setTotalAmount(total.setScale(2, RoundingMode.HALF_UP).doubleValue());

        Order saved = orderRepository.save(order);

        cartRepository.deleteAll(cartItems);

        log.info("Order {} placed for {} ({} paise, payment {})",
                saved.getId(), userEmail, totalPaise, request.getRazorpayPaymentId());

        return saved;
    }

    @Transactional(readOnly = true)
    public List<Order> getUserOrders(String userEmail) {
        return orderRepository.findByUserEmail(userEmail);
    }
}
