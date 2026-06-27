package com.stationary.stationary_backend.controller;

import com.stationary.stationary_backend.dto.request.LoginRequest;
import com.stationary.stationary_backend.dto.request.RefreshRequest;
import com.stationary.stationary_backend.dto.response.ApiResponse;
import com.stationary.stationary_backend.dto.response.AuthResponse;
import com.stationary.stationary_backend.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * AuthController — Handles admin authentication endpoints.
 *
 * ──────────────────────────────────────────────────────────
 * ENDPOINTS:
 * ──────────────────────────────────────────────────────────
 *   POST /api/v1/auth/login    [PUBLIC]  — returns access + refresh token
 *   POST /api/v1/auth/refresh  [PUBLIC]  — exchanges refresh token for new access token
 *   POST /api/v1/auth/logout   [ADMIN]   — clears refresh token, ends session
 *
 * ──────────────────────────────────────────────────────────
 * @AuthenticationPrincipal in logout():
 * ──────────────────────────────────────────────────────────
 * When JwtFilter validates the token, it sets the SecurityContext
 * authentication principal to the adminId (from token subject).
 *
 * @AuthenticationPrincipal String adminId
 *   → Spring injects the principal (adminId) directly into the method param.
 *   This is cleaner than:
 *     SecurityContextHolder.getContext().getAuthentication().getPrincipal()
 *
 * The principal is set in JwtFilter:
 *   new UsernamePasswordAuthenticationToken(adminId, null, List.of(authority))
 *                                           ↑ this is the principal
 *
 * ──────────────────────────────────────────────────────────
 * FRESHER MISTAKE: @RequestBody without @Valid
 * ──────────────────────────────────────────────────────────
 * @RequestBody alone just deserializes JSON to DTO.
 * Validation annotations (@NotBlank etc.) on the DTO are IGNORED
 * unless you also add @Valid before @RequestBody.
 * Always: @Valid @RequestBody YourDto dto
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Admin login.
     * POST /api/v1/auth/login
     * Body: { "username": "admin", "password": "yourpassword" }
     * Returns: { "success": true, "data": { "accessToken": "...", "refreshToken": "..." } }
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {

        AuthResponse authResponse = authService.login(request);
        return ResponseEntity.ok(ApiResponse.of("Login successful", authResponse));
    }

    /**
     * Refresh access token using a valid refresh token.
     * POST /api/v1/auth/refresh
     * Body: { "refreshToken": "..." }
     * Returns: new access + refresh token pair.
     *
     * Frontend should call this ~30 seconds before access token expires.
     * (Use expiresIn from the login response to schedule it.)
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @Valid @RequestBody RefreshRequest request) {

        AuthResponse authResponse = authService.refresh(request.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.of("Token refreshed", authResponse));
    }

    /**
     * Admin logout — clears the refresh token server-side.
     * POST /api/v1/auth/logout
     * Requires: Authorization: Bearer <accessToken>
     *
     * After logout: delete the access + refresh tokens from frontend state.
     * They won't work anyway after refresh token is cleared.
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal String adminId) {

        authService.logout(adminId);
        return ResponseEntity.ok(ApiResponse.ok("Logged out successfully"));
    }
}
