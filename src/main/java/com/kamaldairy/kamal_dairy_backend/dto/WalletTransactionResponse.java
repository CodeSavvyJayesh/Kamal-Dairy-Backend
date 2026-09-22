package com.kamaldairy.kamal_dairy_backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record WalletTransactionResponse(
        Long id,
        String type,
        String source,
        BigDecimal amount,
        BigDecimal balanceAfter,
        String description,
        String referenceId,
        LocalDateTime createdAt
) {}
