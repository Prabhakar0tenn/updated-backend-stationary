package com.stationary.stationary_backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * UnauthorizedException — thrown when credentials are invalid during login.
 *
 * Example:
 *   - Wrong username or password on POST /api/v1/auth/login
 *   - Refresh token doesn't match stored hash
 *   - Admin account is deactivated (isActive = false)
 *
 * Returns: 401 Unauthorized
 *
 * WHY NOT 403?
 * 401 = "Who are you? Prove your identity." (authentication failure)
 * 403 = "I know who you are, but you're not allowed here." (authorization failure)
 *
 * A wrong password is a failure to AUTHENTICATE → 401.
 * Spring Security's JwtFilter handles 401 for missing/invalid tokens
 * (via AuthenticationEntryPoint). This exception handles 401 at the
 * controller/service level (wrong credentials during login).
 *
 * SECURITY NOTE: Don't say "wrong password" — say "invalid credentials".
 * Never hint to an attacker whether the username exists or the password
 * was wrong. Always return the same vague message for both cases.
 */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
