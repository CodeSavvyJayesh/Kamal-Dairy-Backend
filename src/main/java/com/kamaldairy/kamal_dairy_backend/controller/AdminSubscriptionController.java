package com.kamaldairy.kamal_dairy_backend.controller;

import com.kamaldairy.kamal_dairy_backend.dto.*;
import com.kamaldairy.kamal_dairy_backend.service.DeliveryCalendar;
import com.kamaldairy.kamal_dairy_backend.service.SubscriptionAdminService;
import com.kamaldairy.kamal_dairy_backend.service.SubscriptionEngine;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Operations desk for subscriptions. ADMIN only - enforced here and again at
 * the URL level in SecurityConfig.
 */
@RestController
@RequestMapping("/api/admin/subscriptions")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSubscriptionController {

    private final SubscriptionAdminService adminService;
    private final SubscriptionEngine engine;
    private final DeliveryCalendar calendar;

    public AdminSubscriptionController(SubscriptionAdminService adminService,
                                       SubscriptionEngine engine,
                                       DeliveryCalendar calendar) {
        this.adminService = adminService;
        this.engine = engine;
        this.calendar = calendar;
    }

    @GetMapping("/stats")
    public SubscriptionStatsResponse stats() {
        return adminService.stats();
    }

    /** Defaults to tomorrow - the list the dairy packs tonight. */
    @GetMapping("/dispatch")
    public DispatchResponse dispatch(
            @RequestParam(name = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return adminService.dispatch(date == null ? calendar.today().plusDays(1) : date);
    }

    @PostMapping("/deliveries/{id}/delivered")
    public AdminDeliveryResponse markDelivered(@PathVariable("id") Long id) {
        return adminService.markDelivered(id);
    }

    @PostMapping("/deliveries/{id}/refund")
    public AdminDeliveryResponse refund(@PathVariable("id") Long id, @RequestBody(required = false) RefundRequest request) {
        return adminService.refund(id, request == null ? null : request.reason());
    }

    @PostMapping("/run")
    public GenerationSummary run(
            @RequestParam(name = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return engine.runFor(date == null ? calendar.today() : date, "admin");
    }
}
