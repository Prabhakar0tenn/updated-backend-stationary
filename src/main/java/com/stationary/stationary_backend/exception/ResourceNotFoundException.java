package com.stationary.stationary_backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * ResourceNotFoundException — thrown when a requested entity doesn't exist.
 *
 * WHY extend RuntimeException (not Exception)?
 * ──────────────────────────────────────────────────────────
 * Checked exceptions (extends Exception) force every caller
 * to wrap calls in try/catch or declare "throws" — very noisy.
 * RuntimeException is unchecked — it propagates up automatically
 * until caught by GlobalExceptionHandler.
 *
 * This is the standard pattern for Spring REST exception handling.
 * ALL custom business exceptions extend RuntimeException.
 *
 * WHY @ResponseStatus(HttpStatus.NOT_FOUND)?
 * If this exception is NOT caught by GlobalExceptionHandler
 * (e.g., in a background thread), Spring's default error
 * mechanism reads this annotation and returns 404 automatically.
 * It's a safety net. GlobalExceptionHandler is the primary handler.
 *
 * USAGE:
 *   throw new ResourceNotFoundException("Product", "slug", "blue-pen");
 *   → message: "Product not found with slug: blue-pen"
 *
 * FUTURE (v2):
 * Same exception used for Customer, Order, etc.
 * One exception class, multiple entity types. That's the point.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resourceName, String fieldName, Object fieldValue) {
        super(String.format("%s not found with %s: '%s'", resourceName, fieldName, fieldValue));
    }

    // Overload for simple messages like: new ResourceNotFoundException("Category not found")
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
