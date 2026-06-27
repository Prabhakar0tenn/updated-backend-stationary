package com.stationary.stationary_backend.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * AuthResponse — Returned after successful login or token refresh.
 * Sent as: ApiResponse<AuthResponse>
 *
 * ──────────────────────────────────────────────────────────
 * TOKEN STRATEGY (v1 — Simple, Secure Enough for Admin):
 * ──────────────────────────────────────────────────────────
 * accessToken  → short-lived JWT (15 min), stored in React state/memory.
 * refreshToken → long-lived opaque UUID (7 days), returned in body.
 *               Admin stores it and sends it on POST /api/v1/auth/refresh.
 *
 * RULE: Access token goes in Authorization header. NEVER in the URL.
 * Frontend: axios.defaults.headers.common['Authorization'] = `Bearer ${accessToken}`
 *
 * ──────────────────────────────────────────────────────────
 * tokenType: "Bearer" — RFC 6750 standard.
 * expiresIn: seconds until access token expires.
 *   Frontend schedules a refresh BEFORE expiry:
 *   setTimeout(() => refreshToken(), (expiresIn - 30) * 1000)
 */
@Data
@Builder
public class AuthResponse {

    /** JWT access token — 15 min lifespan. Use in Authorization: Bearer header. */
    private String accessToken;

    /** Opaque UUID refresh token — 7 day lifespan. Use to get new access tokens. */
    private String refreshToken;

    /** Always "Bearer" — RFC 6750. */
    private String tokenType;

    /** Seconds until access token expires. Default: 900 (15 min). */
    private long expiresIn;

    /** MongoDB _id of the logged-in admin. */
    private String adminId;

    /** Username of the logged-in admin. Display in admin panel header. */
    private String username;
}
