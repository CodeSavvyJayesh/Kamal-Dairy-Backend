package com.kamaldairy.kamal_dairy_backend.dto;

/** Admin: move an order forward, e.g. {"status": "OUT_FOR_DELIVERY"}. */
public record OrderStatusRequest(String status) {}
