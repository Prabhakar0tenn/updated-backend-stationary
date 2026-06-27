package com.stationary.stationary_backend.exception;

import com.stationary.stationary_backend.dto.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.HashMap;
import java.util.Map;

/**
 * GlobalExceptionHandler — Catches ALL exceptions thrown anywhere in the app
 * and converts them into consistent ApiResponse<Void> JSON responses.
 *
 * ──────────────────────────────────────────────────────────
 * WHY @RestControllerAdvice?
 * ──────────────────────────────────────────────────────────
 * @ControllerAdvice = intercepts exceptions from ALL controllers.
 * @RestControllerAdvice = same, but with @ResponseBody built in
 * so every @ExceptionHandler method returns JSON automatically.
 *
 * Without this, Spring's default error handler returns a generic
 * /error page response. With this, every error is our ApiResponse.
 *
 * ──────────────────────────────────────────────────────────
 * EXCEPTION HANDLING FLOW:
 * ──────────────────────────────────────────────────────────
 * 1. Controller throws exception (or service throws, propagates up)
 * 2. Spring catches it before sending response to client
 * 3. GlobalExceptionHandler picks the right @ExceptionHandler method
 * 4. That method returns ResponseEntity<ApiResponse<Void>> with correct HTTP status
 * 5. Client gets a consistent JSON response
 *
 * ──────────────────────────────────────────────────────────
 * ORDER OF HANDLERS (most specific → least specific):
 * ──────────────────────────────────────────────────────────
 * 1. ResourceNotFoundException   → 404 Not Found
 * 2. DuplicateResourceException  → 409 Conflict
 * 3. BadRequestException         → 400 Bad Request
 * 4. MethodArgumentNotValidException → 400 (validation failures)
 * 5. MissingRequestHeaderException   → 400 (required header absent)
 * 6. MaxUploadSizeExceededException  → 413 Payload Too Large
 * 7. Exception (catch-all)       → 500 Internal Server Error
 *
 * RULE: Handle the most specific exceptions first so Spring
 * picks the right handler. The catch-all must be last.
 *
 * ──────────────────────────────────────────────────────────
 * FRESHER MISTAKE: Throwing in a filter vs. handler
 * ──────────────────────────────────────────────────────────
 * @ExceptionHandler only works for exceptions thrown FROM controllers.
 * Exceptions from Filters (like JwtFilter) bypass this handler.
 * That's why JwtFilter doesn't throw — it just lets the request
 * through unauthenticated. SecurityConfig's AuthenticationEntryPoint
 * handles filter-level auth failures (returns 401).
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 404 — Requested resource (product/category) doesn't exist.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * 409 — Trying to create a resource that already exists.
     * Example: Category with same name, Product with duplicate slug.
     */
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateResource(DuplicateResourceException ex) {
        log.warn("Duplicate resource: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * 400 — General bad request from business logic validation.
     * Example: Deleting a category that has active products.
     */
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(BadRequestException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * 401 — Credential mismatch during login.
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorized(UnauthorizedException ex) {
        log.warn("Unauthorized: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * 400 — Triggered when @Valid fails on a @RequestBody.
     *
     * WHY return a Map of field errors?
     * A single "message" string can't express WHICH fields failed
     * and WHY. Frontend needs field-level errors to highlight
     * the specific inputs that are wrong.
     *
     * Response format:
     * {
     *   "success": false,
     *   "message": "Validation failed",
     *   "data": {
     *     "name": "Product name is required",
     *     "price": "Price must be greater than 0"
     *   }
     * }
     *
     * FRESHER MISTAKE: Not handling this → when validation fails,
     * Spring returns a 400 with Spring's default ugly error body,
     * not your ApiResponse format. Always handle this exception.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationErrors(
            MethodArgumentNotValidException ex) {

        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            // If multiple errors on same field, last one wins — acceptable for v1
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }

        log.warn("Validation failed: {}", fieldErrors);

        // ApiResponse<Map<String,String>> — data field contains the field→message map
        var response = ApiResponse.<Map<String, String>>builder()
                .success(false)
                .message("Validation failed")
                .data(fieldErrors)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * 413 — File size exceeds application.yml's spring.servlet.multipart.max-file-size.
     * Returns a clear message instead of Spring's generic error.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        log.warn("File too large: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.error("File size exceeds the maximum allowed limit of 10MB"));
    }

    /**
     * 400 — Required HTTP header is missing (e.g. X-Session-Id on cart endpoints).
     * Without this, Spring falls through to the catch-all 500 handler.
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingHeader(MissingRequestHeaderException ex) {
        log.warn("Missing required header: {}", ex.getHeaderName());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Required header '" + ex.getHeaderName() + "' is missing"));
    }

    /**
     * 500 — Catch-all for unexpected exceptions.
     *
     * WHY log at ERROR level here (not warn)?
     * All the above handlers are "expected" business errors.
     * Anything reaching here is genuinely unexpected — a bug.
     * ERROR level triggers alerts in prod monitoring.
     *
     * WHY NOT expose ex.getMessage() to the client?
     * The message might contain stack traces, DB details, or
     * internal class names — all dangerous to expose publicly.
     * Return a generic message; log the full exception server-side.
     *
     * RULE: Never return raw exception messages from 500 handlers.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unexpected error: ", ex);  // full stack trace in server logs
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected error occurred. Please try again later."));
    }
}
