package com.kamaldairy.kamal_dairy_backend.dto;

public record DeliveryAddress(
        String name,
        String phone,
        String address,
        String city,
        String pincode
) {}
