package com.kamaldairy.kamal_dairy_backend.controller;

import com.kamaldairy.kamal_dairy_backend.dto.CancelOrderRequest;
import com.kamaldairy.kamal_dairy_backend.dto.OrderStatsResponse;
import com.kamaldairy.kamal_dairy_backend.dto.OrderStatusRequest;
import com.kamaldairy.kamal_dairy_backend.dto.PageResponse;
import com.kamaldairy.kamal_dairy_backend.model.Order;
import com.kamaldairy.kamal_dairy_backend.service.InvoiceService;
import com.kamaldairy.kamal_dairy_backend.service.OrderLifecycleService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Order desk. ADMIN only - enforced here and again at the URL level in
 * SecurityConfig.
 */
@RestController
@RequestMapping("/api/admin/orders")
@PreAuthorize("hasRole('ADMIN')")
public class AdminOrderController {

    private final OrderLifecycleService lifecycle;
    private final InvoiceService invoiceService;

    public AdminOrderController(OrderLifecycleService lifecycle, InvoiceService invoiceService) {
        this.lifecycle = lifecycle;
        this.invoiceService = invoiceService;
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

    /** Any customer's invoice, for a reprint or a query on the phone. */
    @GetMapping("/{id}/invoice")
    public ResponseEntity<byte[]> invoice(@PathVariable("id") Integer id) {
        return Invoices.asPdf(invoiceService.forAdmin(id));
    }

    /**
     * The invoice register for a period, as CSV: one row per item line, with the
     * taxable value and tax split out. This is the file that goes to the
     * accountant at the end of the month.
     */
    @GetMapping("/invoice-register.csv")
    public ResponseEntity<byte[]> register(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Invoices.asCsv(invoiceService.csv(from, to),
                "Kamal-Dairy-invoices-" + from + "-to-" + to + ".csv");
    }
}
