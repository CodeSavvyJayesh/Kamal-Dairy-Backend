package com.kamaldairy.kamal_dairy_backend.controller;

import com.kamaldairy.kamal_dairy_backend.dto.SalesAnalyticsResponse;
import com.kamaldairy.kamal_dairy_backend.service.AnalyticsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Admin Insights. ADMIN only - enforced here and again in SecurityConfig. */
@RestController
@RequestMapping("/api/admin/analytics")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAnalyticsController {

    private final AnalyticsService analyticsService;

    public AdminAnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /** The last N days including today (7 to 365), compared with the N days before. */
    @GetMapping("/sales")
    public SalesAnalyticsResponse sales(@RequestParam(name = "days", defaultValue = "30") int days) {
        return analyticsService.sales(days);
    }
}
