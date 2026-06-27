package com.stationary.stationary_backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * DuplicateResourceException — thrown when trying to create a resource
 * that violates a uniqueness constraint.
 *
 * Examples:
 *   - Category with name "Pens" already exists
 *   - Product slug "blue-gel-pen" already taken after collision attempts
 *
 * Returns: 409 Conflict
 *
 * WHY 409 and not 400?
 * 400 = "your request is malformed" (bad data)
 * 409 = "your request is valid, but conflicts with existing state"
 * A duplicate name is a valid name — it just conflicts. 409 is correct.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String resourceName, String fieldName, Object fieldValue) {
        super(String.format("%s already exists with %s: '%s'", resourceName, fieldName, fieldValue));
    }

    public DuplicateResourceException(String message) {
        super(message);
    }
}
