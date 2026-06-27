package com.stationary.stationary_backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * LoginRequest — What the admin sends to POST /api/v1/auth/login
 *
 * ──────────────────────────────────────────────────────────
 * WHY validation annotations on REQUEST DTOs specifically?
 * ──────────────────────────────────────────────────────────
 * Request DTOs are the first thing your code touches after JSON
 * deserialization. Validate there — before any business logic.
 * This creates a clean "input sanitization" boundary.
 *
 * If you validate in the service layer instead, you scatter
 * validation logic across the codebase. Every service method
 * would need to check if fields are null. Noisy, duplicated, forgettable.
 *
 * ──────────────────────────────────────────────────────────
 * @NotBlank vs @NotNull vs @NotEmpty:
 * ──────────────────────────────────────────────────────────
 * @NotNull   — field can't be null. But "" (empty string) passes.
 * @NotEmpty  — field can't be null or "". But "   " (spaces) passes.
 * @NotBlank  — field can't be null, "", or "   ". Strongest.
 *
 * For text inputs from forms/JSON, ALWAYS use @NotBlank.
 * @NotNull alone on a String is almost never what you want.
 *
 * ──────────────────────────────────────────────────────────
 * WHY @Size(min=8) on password?
 * ──────────────────────────────────────────────────────────
 * Minimum length rejects obvious bad inputs before hashing.
 * BCrypt is intentionally slow — don't waste 50ms hashing "a".
 * Also signals to the caller that weak passwords aren't accepted.
 */
@Data
public class LoginRequest {

    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;
}
