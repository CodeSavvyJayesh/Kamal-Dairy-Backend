package com.kamaldairy.kamal_dairy_backend.controller;

import com.kamaldairy.kamal_dairy_backend.dto.*;
import com.kamaldairy.kamal_dairy_backend.service.SubscriptionService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Customer subscription API. The owner is always the authenticated principal;
 * no endpoint accepts an email, and a subscription id belonging to someone
 * else is simply "not found".
 */
@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    // ---- public

    @GetMapping("/plans")
    public SubscriptionPlansResponse plans() {
        return subscriptionService.plans();
    }

    @PostMapping("/preview")
    public SubscriptionPreviewResponse preview(@RequestBody SubscriptionRequest request) {
        return subscriptionService.preview(request);
    }

    // ---- signed in

    @GetMapping
    public List<SubscriptionResponse> mine(Authentication auth) {
        return subscriptionService.list(auth.getName());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SubscriptionResponse create(@RequestBody SubscriptionRequest request, Authentication auth) {
        return subscriptionService.create(auth.getName(), request);
    }

    @GetMapping("/deliveries")
    public PageResponse<DeliveryResponse> deliveries(
            @RequestParam(name = "subscriptionId", required = false) Long subscriptionId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            Authentication auth) {
        return PageResponse.of(subscriptionService.deliveries(auth.getName(), subscriptionId, page, size));
    }

    @GetMapping("/{id}")
    public SubscriptionDetailResponse detail(@PathVariable("id") Long id, Authentication auth) {
        return subscriptionService.detail(auth.getName(), id);
    }

    @PutMapping("/{id}")
    public SubscriptionDetailResponse update(@PathVariable("id") Long id, @RequestBody SubscriptionRequest request,
                                             Authentication auth) {
        return subscriptionService.update(auth.getName(), id, request);
    }

    @PostMapping("/{id}/pause")
    public SubscriptionDetailResponse pause(@PathVariable("id") Long id, Authentication auth) {
        return subscriptionService.pause(auth.getName(), id);
    }

    @PostMapping("/{id}/resume")
    public SubscriptionDetailResponse resume(@PathVariable("id") Long id, Authentication auth) {
        return subscriptionService.resume(auth.getName(), id);
    }

    @PostMapping("/{id}/cancel")
    public SubscriptionDetailResponse cancel(@PathVariable("id") Long id, Authentication auth) {
        return subscriptionService.cancel(auth.getName(), id);
    }

    @PutMapping("/{id}/vacation")
    public SubscriptionDetailResponse setVacation(@PathVariable("id") Long id, @RequestBody VacationRequest request,
                                                  Authentication auth) {
        return subscriptionService.setVacation(auth.getName(), id, request);
    }

    @DeleteMapping("/{id}/vacation")
    public SubscriptionDetailResponse clearVacation(@PathVariable("id") Long id, Authentication auth) {
        return subscriptionService.clearVacation(auth.getName(), id);
    }

    @PostMapping("/{id}/skips")
    public SubscriptionDetailResponse skip(@PathVariable("id") Long id, @RequestBody SkipRequest request,
                                           Authentication auth) {
        return subscriptionService.skip(auth.getName(), id, request);
    }

    @DeleteMapping("/{id}/skips/{date}")
    public SubscriptionDetailResponse unskip(@PathVariable("id") Long id,
                                             @PathVariable("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                             Authentication auth) {
        return subscriptionService.unskip(auth.getName(), id, date);
    }
}
