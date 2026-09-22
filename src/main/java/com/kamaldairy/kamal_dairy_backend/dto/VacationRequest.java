package com.kamaldairy.kamal_dairy_backend.dto;

import java.time.LocalDate;

public record VacationRequest(LocalDate from, LocalDate to) {}
