package com.kamaldairy.kamal_dairy_backend.controller;

import com.kamaldairy.kamal_dairy_backend.dto.PaymentOrderResponse;
import com.kamaldairy.kamal_dairy_backend.service.PaymentService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payment")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * Creates a Razorpay order for the signed-in user's cart.
     *
     * The amount is no longer a request parameter. It is computed on the
     * server from the cart and the products table, and stored so it can be
     * checked again when the order is placed.
     */
    @PostMapping("/create-order")
    public PaymentOrderResponse createOrder(Authentication authentication) throws Exception {
        return paymentService.createOrderForCart(authentication.getName());
    }
}
