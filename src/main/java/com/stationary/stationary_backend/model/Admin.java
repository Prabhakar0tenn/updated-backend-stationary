package com.stationary.stationary_backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Admin — MongoDB document for the "admins" collection.
 *
 * ──────────────────────────────────────────────────────────
 * SECURITY — Field naming matters:
 * ──────────────────────────────────────────────────────────
 * Field is named "passwordHash" not "password".
 * This is intentional — it makes it OBVIOUS to every developer
 * reading this class that this field is a hash, not plaintext.
 * If someone ever sees "password" and tries to store plaintext,
 * the name itself signals the mistake.
 *
 * RULE: Never log, serialize, or return passwordHash in any
 * response. The @JsonIgnore comment reminds you of this —
 * we'll add it explicitly on the response DTO in Step 4.
 *
 * ──────────────────────────────────────────────────────────
 * refreshTokenHash — WHY store a hash, not the token?
 * ──────────────────────────────────────────────────────────
 * Refresh tokens are opaque random strings (like UUID).
 * If you store the raw token and your DB is compromised,
 * the attacker has valid refresh tokens and can generate
 * access tokens indefinitely.
 *
 * Store a SHA-256 hash of the token in DB.
 * When the client sends a refresh token:
 *   1. Hash it → compare with stored hash
 *   2. Match = valid, issue new access token
 *   3. No match = reject
 *
 * Same principle as password hashing — never store the secret itself.
 *
 * WHY nullable? Not every login flow immediately issues a refresh
 * token (e.g., first login). Nullable is correct.
 *
 * ──────────────────────────────────────────────────────────
 * isActive — WHY needed for Admin?
 * ──────────────────────────────────────────────────────────
 * v1: only you use the admin. But if you ever add a co-admin
 * in v2, you need to disable their access without deleting
 * the account (audit trail). isActive = false blocks login.
 *
 * ──────────────────────────────────────────────────────────
 * lastLoginAt — WHY track this?
 * ──────────────────────────────────────────────────────────
 * Useful for:
 *   - Security: detect suspicious "last login was 3 months ago
 *     but there were 100 API calls yesterday"
 *   - Admin dashboard: show "last logged in: 2 hours ago"
 * Cost: one extra DB write per login. Worth it.
 *
 * ──────────────────────────────────────────────────────────
 * FUTURE (v2 — Customer model):
 * ──────────────────────────────────────────────────────────
 * Customer will be a SEPARATE document (separate collection).
 * Do NOT put ROLE field on Admin to distinguish admin from customer.
 * Having a Customer document separate from Admin document is
 * cleaner and doesn't mix unrelated concerns.
 */
@Document("admins")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Admin {

    @Id
    private String id;

    /**
     * Login username. Must be unique.
     * Used as the login identifier (not email — admin prefers short names).
     */
    @Indexed(unique = true)
    private String username;

    /**
     * Admin's email address. Unique, for future password reset flow.
     * Not used for login in v1 — username is the login identifier.
     */
    @Indexed(unique = true)
    private String email;

    /**
     * BCrypt hash of the admin's password.
     * NEVER plain text. NEVER logged. NEVER serialized to JSON.
     * BCrypt(10) = 60-character hash string starting with "$2a$10$..."
     */
    private String passwordHash;

    /**
     * SHA-256 hash of the most recently issued refresh token.
     * null when no active refresh token exists (e.g., logged out).
     * Replaced on every new login. One active session at a time.
     *
     * WHY one session? For a solo admin, this is sufficient.
     * If multiple devices are needed in v2, change to List<String>.
     */
    private String refreshTokenHash;

    /**
     * Account enable/disable flag.
     * false = cannot login, all tokens rejected.
     * Set by the AdminSeedRunner to true on creation.
     */
    private Boolean isActive;

    /**
     * Timestamp of most recent successful login.
     * Updated in AuthService.login() after successful password verify.
     */
    private Instant lastLoginAt;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
