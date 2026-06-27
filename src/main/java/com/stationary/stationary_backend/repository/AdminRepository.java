package com.stationary.stationary_backend.repository;

import com.stationary.stationary_backend.model.Admin;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

/**
 * AdminRepository — Data access layer for Admin accounts.
 *
 * ──────────────────────────────────────────────────────────
 * HOW Admin data flows through the application:
 * ──────────────────────────────────────────────────────────
 *
 * Login flow:
 *   1. AdminRepository.findByUsername(username)   ← fetch admin
 *   2. BCrypt.matches(rawPassword, admin.passwordHash)  ← verify
 *   3. JwtUtil.generateToken(admin.id, admin.username, "ROLE_ADMIN")
 *   4. Admin.lastLoginAt = Instant.now() → save
 *
 * Refresh token flow:
 *   1. Client sends refresh token in cookie
 *   2. AdminRepository.findByRefreshTokenHash(hash)  ← look up admin
 *   3. Issue new access token
 *   4. Rotate refresh token → save new hash
 *
 * Startup seed flow (AdminSeedRunner):
 *   1. existsByUsername(seedUsername)  ← check if already seeded
 *   2. If false → create admin → save
 *   3. If true → skip (idempotent — safe to run on every restart)
 *
 * ──────────────────────────────────────────────────────────
 * WHY findByUsername for login (not findByEmail)?
 * ──────────────────────────────────────────────────────────
 * Two reasons:
 *   1. Admin typed the username to log in — that's the identifier.
 *   2. Email is stored for contact/recovery purposes, not primary auth.
 *
 * Allowing login by email too is fine in v2 — just add
 * findByEmail(String email) and check both in AuthService.
 *
 * ──────────────────────────────────────────────────────────
 * findByRefreshTokenHash — the security detail:
 * ──────────────────────────────────────────────────────────
 * The client sends the raw refresh token (UUID).
 * AuthService hashes it (SHA-256) then calls this method.
 * The stored value is always a hash — raw token never in DB.
 *
 * WHY not findById + compare hash in service?
 * We don't know WHICH admin's token it is without the admin ID.
 * The client only sends the token, not their ID.
 * So we look up by the token hash directly.
 *
 * Performance: This is a rare operation (once per 7 days per user).
 * Single admin in v1 = negligible. Index can be added in v2 if needed.
 *
 * ──────────────────────────────────────────────────────────
 * FUTURE (v2 — if multiple admins):
 * ──────────────────────────────────────────────────────────
 * Add: List<Admin> findByIsActiveTrue()  → list all active admins
 * Add: @Indexed on refreshTokenHash field in Admin model for speed
 */
public interface AdminRepository extends MongoRepository<Admin, String> {

    /**
     * Fetch admin by username for login authentication.
     * Returns Optional — empty if username doesn't exist.
     * Service layer handles the empty case with proper error.
     *
     * → db.admins.find({ username: "admin" })
     */
    Optional<Admin> findByUsername(String username);

    /**
     * Check if a username is already taken.
     * Used by AdminSeedRunner: "is there already an admin named 'admin'?"
     * Returns boolean directly — no Optional dance needed.
     *
     * → db.admins.find({ username: "admin" }) → count > 0
     */
    boolean existsByUsername(String username);

    /**
     * Look up admin by refresh token hash.
     * Client sends raw UUID → service SHA-256 hashes it → look up here.
     * Returns Optional — empty if token hash doesn't match any admin
     * (invalid/expired/rotated token).
     *
     * → db.admins.find({ refreshTokenHash: "sha256hash..." })
     */
    Optional<Admin> findByRefreshTokenHash(String refreshTokenHash);
}
