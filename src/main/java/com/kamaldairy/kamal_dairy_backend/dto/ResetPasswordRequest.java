package com.kamaldairy.kamal_dairy_backend.dto;

public record ResetPasswordRequest(String email, String code, String newPassword) {}
