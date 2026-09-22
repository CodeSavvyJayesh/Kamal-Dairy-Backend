package com.kamaldairy.kamal_dairy_backend.dto;

/**
 * Body for the two checkout calls that have no payment receipt yet:
 * POST /api/payment/create-order and POST /api/orders/place-with-wallet.
 */
public record CheckoutRequest(DeliveryAddress address) {}
