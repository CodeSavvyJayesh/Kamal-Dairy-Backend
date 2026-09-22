package com.kamaldairy.kamal_dairy_backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DispatchResponse(
        LocalDate date,
        boolean generated,
        int total,
        int scheduled,
        int delivered,
        int missed,
        int refunded,
        int totalUnits,
        BigDecimal revenue,
        List<AdminDeliveryResponse> deliveries
) {}
