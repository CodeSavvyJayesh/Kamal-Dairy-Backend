package com.kamaldairy.kamal_dairy_backend.dto;

import java.time.LocalDate;

public record GenerationSummary(
        LocalDate date,
        boolean ran,
        int considered,
        int charged,
        int missedLowBalance,
        int missedUnavailable,
        int alreadyExisted,
        int failed
) {
    public static GenerationSummary notRun(LocalDate date) {
        return new GenerationSummary(date, false, 0, 0, 0, 0, 0, 0);
    }
}
