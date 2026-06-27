package com.stationary.stationary_backend.service;

import com.stationary.stationary_backend.dto.request.LoginRequest;
import com.stationary.stationary_backend.dto.response.AuthResponse;
import com.stationary.stationary_backend.exception.BadRequestException;
import com.stationary.stationary_backend.exception.UnauthorizedException;
import com.stationary.stationary_backend.model.Admin;
import com.stationary.stationary_backend.repository.AdminRepository;
import com.stationary.stationary_backend.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/**
 * AuthService — Handles all authentication logic.
 *
 * ──────────────────────────────────────────────────────────
 * FLOWS:
 * ──────────────────────────────────────────────────────────
 * 1. login()   → verify credentials → issue access + refresh tokens
 * 2. refresh() → verify refresh token → rotate and issue new tokens
 * 3. logout()  → clear refresh token hash from DB
 *
 * ──────────────────────────────────────────────────────────
 * REFRESH TOKEN DESIGN:
 * ──────────────────────────────────────────────────────────
 * Access token:  Short-lived JWT (15 min). Stateless. No DB lookup.
 * Refresh token: Long-lived opaque UUID (7 days). Stored as SHA-256 hash in DB.
 *
 * WHY hash the refresh token before storing?
 * If DB is compromised, raw tokens let attackers generate access tokens silently.
 * We store SHA-256(token). Hash cannot be reversed to a valid token.
 *
 * WHY SHA-256 not BCrypt for refresh token?
 * Refresh tokens are 128-bit random UUIDs — already cryptographically strong.
 * BCrypt's slowness is for weak user passwords. Not needed here.
 * SHA-256 is fast and appropriate for high-entropy random values.
 *
 * ──────────────────────────────────────────────────────────
 * SECURITY: Same error message for wrong username AND wrong password.
 * ──────────────────────────────────────────────────────────
 * "Invalid credentials" → tells attacker nothing.
 * "Username not found" → reveals which usernames exist (enumeration attack).
 * Always use the same vague message.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Value("${jwt.access-token-expiry-ms}")
    private long accessTokenExpiryMs;

    // ── Login ─────────────────────────────────────────────────────────────

    /**
     * Authenticates admin and issues access + refresh tokens.
     *
     * @param request username + password
     * @return AuthResponse with tokens + admin info
     */
    public AuthResponse login(LoginRequest request) {
        // Step 1: Find admin — same error whether username wrong or account disabled
        Admin admin = adminRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        // Step 2: Check account active BEFORE expensive BCrypt check
        if (!Boolean.TRUE.equals(admin.getIsActive())) {
            throw new UnauthorizedException("Invalid credentials");
        }

        // Step 3: Verify password with BCrypt (intentionally slow — ~100ms)
        // WHY passwordEncoder.matches() and NOT .equals()?
        // BCrypt stores salt inside the hash. matches() extracts salt, rehashes,
        // then does constant-time comparison to prevent timing attacks.
        if (!passwordEncoder.matches(request.getPassword(), admin.getPasswordHash())) {
            throw new UnauthorizedException("Invalid credentials");
        }

        // Step 4: Generate tokens
        String accessToken = jwtUtil.generateToken(admin.getId(), admin.getUsername(), "ROLE_ADMIN");
        String rawRefreshToken = generateRefreshToken();

        // Step 5: Persist refresh token hash + update last login
        admin.setRefreshTokenHash(sha256(rawRefreshToken));
        admin.setLastLoginAt(Instant.now());
        adminRepository.save(admin);

        log.info("Admin '{}' logged in successfully", admin.getUsername());

        return buildAuthResponse(accessToken, rawRefreshToken, admin);
    }

    // ── Refresh ───────────────────────────────────────────────────────────

    /**
     * Issues new tokens given a valid refresh token.
     * Rotates the refresh token — old one is invalidated immediately.
     *
     * @param rawRefreshToken opaque UUID from client
     * @return AuthResponse with fresh tokens
     */
    public AuthResponse refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new BadRequestException("Refresh token is required");
        }

        // Hash the incoming token to look it up in DB
        String incomingHash = sha256(rawRefreshToken);

        Admin admin = adminRepository.findByRefreshTokenHash(incomingHash)
                .orElseThrow(() -> new BadRequestException("Invalid or expired refresh token"));

        if (!Boolean.TRUE.equals(admin.getIsActive())) {
            throw new BadRequestException("Account is disabled");
        }

        // Rotate: generate brand-new tokens
        String newAccessToken = jwtUtil.generateToken(admin.getId(), admin.getUsername(), "ROLE_ADMIN");
        String newRawRefreshToken = generateRefreshToken();

        // Overwrite stored hash — old refresh token is now dead
        admin.setRefreshTokenHash(sha256(newRawRefreshToken));
        adminRepository.save(admin);

        log.debug("Refresh token rotated for admin '{}'", admin.getUsername());

        return buildAuthResponse(newAccessToken, newRawRefreshToken, admin);
    }

    // ── Logout ────────────────────────────────────────────────────────────

    /**
     * Clears refresh token from DB. Access token still works until it expires (~15 min).
     *
     * WHY not revoke access tokens?
     * JWTs are stateless — revocation requires a DB/Redis blacklist.
     * For a solo admin, 15-min window is acceptable in v1.
     * Add Redis token blacklist in v2 if instant revocation is needed.
     *
     * @param adminId extracted from JWT by JwtFilter (SecurityContext principal)
     */
    public void logout(String adminId) {
        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new UnauthorizedException("Admin not found"));
        admin.setRefreshTokenHash(null);
        adminRepository.save(admin);
        log.info("Admin '{}' logged out", admin.getUsername());
    }

    // ── Internal helpers ──────────────────────────────────────────────────

    private AuthResponse buildAuthResponse(String accessToken, String rawRefreshToken, Admin admin) {
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefreshToken)
                .tokenType("Bearer")
                .expiresIn(accessTokenExpiryMs / 1000)   // convert ms → seconds for frontend
                .adminId(admin.getId())
                .username(admin.getUsername())
                .build();
    }

    /**
     * Generates a cryptographically random refresh token (UUID without dashes).
     * 128 bits of SecureRandom entropy. Brute-force resistant.
     */
    private String generateRefreshToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * SHA-256 hash of a string. Used to hash refresh tokens before DB storage.
     *
     * SHA-256 is guaranteed by Java spec — NoSuchAlgorithmException is impossible.
     * Wrapped in RuntimeException so callers don't need to handle a phantom checked exception.
     */
    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
