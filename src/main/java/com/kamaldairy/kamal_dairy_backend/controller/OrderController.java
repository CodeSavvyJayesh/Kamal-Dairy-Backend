package com.kamaldairy.kamal_dairy_backend.controller;

import com.kamaldairy.kamal_dairy_backend.dto.CancelOrderRequest;
import com.kamaldairy.kamal_dairy_backend.dto.CheckoutRequest;
import com.kamaldairy.kamal_dairy_backend.dto.PlaceOrderRequest;
import com.kamaldairy.kamal_dairy_backend.model.Order;
import com.kamaldairy.kamal_dairy_backend.service.OrderLifecycleService;
import com.kamaldairy.kamal_dairy_backend.service.OrderService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final OrderLifecycleService lifecycle;

    public OrderController(OrderService orderService, OrderLifecycleService lifecycle) {
        this.orderService = orderService;
        this.lifecycle = lifecycle;
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

    /**
     * Pays for the cart from the prepaid wallet. 402 if the balance is short,
     * in which case nothing is ordered and nothing is charged.
     */
    @PostMapping("/place-with-wallet")
    public Order placeOrderWithWallet(
            @RequestBody(required = false) CheckoutRequest request,
            Authentication authentication
    ) {
        return orderService.placeOrderWithWallet(
                authentication.getName(), request == null ? null : request.address());
    }

    /**
     * Cancels the caller's own order while it is still PLACED. The full
     * amount goes back to the Kamal Wallet and the items back on the shelf.
     */
    @PostMapping("/{id}/cancel")
    public Order cancel(
            @PathVariable("id") Integer id,
            @RequestBody(required = false) CancelOrderRequest request,
            Authentication authentication
    ) {
        return lifecycle.cancelByCustomer(authentication.getName(), id,
                request == null ? null : request.reason());
    }

    @GetMapping("/my-orders")
    public List<Order> getMyOrders(Authentication authentication) {
        return orderService.getUserOrders(authentication.getName());
    }
}
