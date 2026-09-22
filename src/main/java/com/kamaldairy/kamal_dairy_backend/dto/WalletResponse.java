package com.kamaldairy.kamal_dairy_backend.dto;

import java.math.BigDecimal;
import java.util.List;

public record WalletResponse(
        BigDecimal balance,
        WalletForecast forecast,
        List<WalletTransactionResponse> recent,
        BigDecimal minTopup,
        BigDecimal maxTopup
) {}
