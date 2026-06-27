package com.stationary.stationary_backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JwtUtil — Creates and validates JSON Web Tokens.
 *
 * ──────────────────────────────────────────────────────────
 * WHAT IS A JWT?
 * ──────────────────────────────────────────────────────────
 * A JWT has 3 parts separated by dots:
 *   header.payload.signature
 *
 *   Header:    algorithm used (HS256)
 *   Payload:   claims = data stored inside the token
 *              (adminId, username, role, expiry)
 *   Signature: HMAC of header+payload using secret key
 *              → proves the token wasn't tampered with
 *
 * WHY stateless?
 * The server doesn't store access tokens anywhere.
 * It just verifies the signature on every request.
 * This is what makes JWTs scalable — no DB lookup per request.
 *
 * ──────────────────────────────────────────────────────────
 * ALGORITHM CHOICE: HS256 (HMAC-SHA256)
 * ──────────────────────────────────────────────────────────
 * HS256 = symmetric. Same key signs AND verifies.
 * RS256 = asymmetric. Private key signs, public key verifies.
 *
 * For a solo backend (no separate auth server), HS256 is correct.
 * RS256 is needed when a third-party service needs to verify
 * your tokens without knowing your secret.
 *
 * ──────────────────────────────────────────────────────────
 * FUTURE (v2):
 * When CUSTOMER login is added, this class doesn't change.
 * Just pass role="ROLE_CUSTOMER" when generating their token.
 * The same generateToken() method handles both roles.
 * ──────────────────────────────────────────────────────────
 */
@Component
public class JwtUtil {

    private final SecretKey secretKey;
    private final long accessTokenExpiryMs;

    /**
     * WHY constructor injection instead of @Value on fields?
     * Because SecretKey needs to be DERIVED from the secret string,
     * not directly injected. Constructor lets us do that derivation
     * once at startup, not on every token operation.
     */
    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiry-ms}") long accessTokenExpiryMs
    ) {
        // Keys.hmacShaKeyFor() derives a SecretKey from raw bytes.
        // WHY getBytes(UTF_8)? The secret in YAML is a String.
        // We need it as bytes to create a cryptographic key.
        // IMPORTANT: secret must be ≥ 32 chars for HS256 (256 bits).
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiryMs = accessTokenExpiryMs;
    }

    /**
     * Generates a signed JWT access token.
     *
     * @param adminId   MongoDB _id of the admin (subject of the token)
     * @param username  admin's username (stored as a claim)
     * @param role      e.g. "ROLE_ADMIN" — Spring Security reads this prefix
     * @return signed JWT string
     *
     * WHY store role in the token?
     * So we don't query the DB on every request just to check role.
     * The role is verified via the token signature — if the token
     * is valid, the role inside it is trusted.
     *
     * TRADEOFF: If you revoke an admin, their token still works
     * until it expires (15 min). This is the JWT tradeoff.
     * For admin-only auth, 15 min is acceptable.
     */
    public String generateToken(String adminId, String username, String role) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("username", username);
        extraClaims.put("role", role);

        return Jwts.builder()
                .claims(extraClaims)
                .subject(adminId)               // who this token is about
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpiryMs))
                .signWith(secretKey)            // signs with HS256 by default for SecretKey
                .compact();
    }

    /**
     * Extracts all claims (payload) from a token.
     * Throws JwtException if token is invalid or expired.
     *
     * WHY return Claims (not individual fields)?
     * Callers can then extract whatever they need —
     * subject (adminId), role, username, expiry — without
     * this method needing to know what the caller wants.
     */
    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)          // verifies signature
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Extracts the subject (adminId) from a token.
     * WHY adminId as subject? It's the stable identifier.
     * Username could change; MongoDB _id never does.
     */
    public String extractAdminId(String token) {
        return extractAllClaims(token).getSubject();
    }

    public String extractRole(String token) {
        return extractAllClaims(token).get("role", String.class);
    }

    public String extractUsername(String token) {
        return extractAllClaims(token).get("username", String.class);
    }

    /**
     * Validates a token — returns true if valid, false otherwise.
     * Never throws — callers don't need to handle exceptions.
     *
     * WHY catch Exception broadly here?
     * JJWT throws different subtypes:
     *   ExpiredJwtException    — token expired
     *   MalformedJwtException  — tampered/garbage
     *   SignatureException     — wrong key
     *   UnsupportedJwtException — wrong format
     * All of these mean "invalid token". Catching the parent
     * JwtException handles all of them cleanly.
     */
    public boolean isTokenValid(String token) {
        try {
            extractAllClaims(token); // throws if invalid
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
