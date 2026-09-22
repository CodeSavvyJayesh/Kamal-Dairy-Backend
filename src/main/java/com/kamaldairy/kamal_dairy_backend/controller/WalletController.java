package com.kamaldairy.kamal_dairy_backend.controller;

import com.kamaldairy.kamal_dairy_backend.dto.*;
import com.kamaldairy.kamal_dairy_backend.service.SubscriptionService;
import com.kamaldairy.kamal_dairy_backend.service.WalletService;
import com.kamaldairy.kamal_dairy_backend.util.Money;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    private final WalletService walletService;
    private final SubscriptionService subscriptionService;

    public WalletController(WalletService walletService, SubscriptionService subscriptionService) {
        this.walletService = walletService;
        this.subscriptionService = subscriptionService;
    }

    @GetMapping
    public WalletResponse wallet(Authentication auth) {
        return summary(auth.getName());
    }

    @GetMapping("/transactions")
    public PageResponse<WalletTransactionResponse> transactions(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            Authentication auth) {
        return PageResponse.of(walletService.transactions(auth.getName(), page, size));
    }

    /** Step 1 of a top-up: returns a Razorpay order for the chosen whole-rupee amount. */
    @PostMapping("/topup")
    public PaymentOrderResponse createTopup(@RequestBody TopupRequest request, Authentication auth) throws Exception {
        return walletService.createTopup(auth.getName(), request == null ? null : request.amount());
    }

    /** Step 2: hand back Razorpay's signed receipt; the wallet is credited only if it verifies. */
    @PostMapping("/topup/verify")
    public WalletResponse verifyTopup(@RequestBody PlaceOrderRequest request, Authentication auth) {
        walletService.verifyTopup(auth.getName(), request);
        return summary(auth.getName());
    }

    private WalletResponse summary(String email) {
        long balance = walletService.balancePaise(email);
        return new WalletResponse(
                Money.toRupees(balance),
                subscriptionService.forecast(email, balance),
                walletService.recent(email),
                Money.toRupees(WalletService.MIN_TOPUP_PAISE),
                Money.toRupees(WalletService.MAX_TOPUP_PAISE));
    }
}
