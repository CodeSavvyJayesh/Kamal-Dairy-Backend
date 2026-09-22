package com.kamaldairy.kamal_dairy_backend.dto;

/** Admin: set the exact count on the shelf. null means "do not track stock" (always available). */
public record StockRequest(Integer stock) {}
