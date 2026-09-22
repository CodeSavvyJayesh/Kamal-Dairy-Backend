package com.kamaldairy.kamal_dairy_backend.controller;

import com.kamaldairy.kamal_dairy_backend.dto.CancelOrderRequest;
import com.kamaldairy.kamal_dairy_backend.dto.OrderStatsResponse;
import com.kamaldairy.kamal_dairy_backend.dto.OrderStatusRequest;
import com.kamaldairy.kamal_dairy_backend.dto.PageResponse;
import com.kamaldairy.kamal_dairy_backend.model.Order;
import com.kamaldairy.kamal_dairy_backend.service.OrderLifecycleService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Order desk. ADMIN only - enforced here and again at the URL level in
 * SecurityConfig.
 */
@RestController
@RequestMapping("/api/admin/orders")
@PreAuthorize("hasRole('ADMIN')")
public class AdminOrderController {

    private final OrderLifecycleService lifecycle;

    public AdminOrderController(OrderLifecycleService lifecycle) {
        this.lifecycle = lifecycle;
    }

    /** Newest first. status = ALL (default), OPEN, PLACED, CONFIRMED, OUT_FOR_DELIVERY, DELIVERED or CANCELLED. */
    @GetMapping
    public PageResponse<Order> list(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return PageResponse.of(lifecycle.list(status, page, size));
    }

    @GetMapping("/stats")
    public OrderStatsResponse stats() {
        return lifecycle.stats();
    }

    /** Move forward: {"status": "CONFIRMED" | "OUT_FOR_DELIVERY" | "DELIVERED"}. */
    @PostMapping("/{id}/status")
    public Order advance(@PathVariable("id") Integer id, @RequestBody OrderStatusRequest request) {
        return lifecycle.advance(id, request == null ? null : request.status());
    }

    /** Cancel any open order: full refund to the customer's wallet, items back on the shelf. */
    @PostMapping("/{id}/cancel")
    public Order cancel(@PathVariable("id") Integer id,
                        @RequestBody(required = false) CancelOrderRequest request) {
        return lifecycle.cancelByAdmin(id, request == null ? null : request.reason());
    }
}
