package com.stationary.stationary_backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * BadRequestException — thrown for business-rule violations that
 * are NOT about missing/invalid fields (those are handled by @Valid).
 *
 * Examples:
 *   - Admin tries to deactivate a category that still has active products
 *   - Admin tries to upload a non-image file type
 *   - Refresh token is invalid or expired
 *
 * Returns: 400 Bad Request
 *
 * WHY separate from MethodArgumentNotValidException?
 * @Valid catches structural validation (is field present? is it a number?).
 * BadRequestException catches semantic validation (is this action allowed?).
 * They're different concerns, handled at different layers.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
