package com.kamaldairy.kamal_dairy_backend.dto;

/** Admin: add this many units to what is already on the shelf. */
public record RestockRequest(Integer quantity) {}
