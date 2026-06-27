package com.stationary.stationary_backend.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * ApiResponse<T> — Universal wrapper for every HTTP response.
 *
 * ──────────────────────────────────────────────────────────
 * WHY wrap all responses in a standard envelope?
 * ──────────────────────────────────────────────────────────
 * Without a wrapper, your API returns inconsistent shapes:
 *   Success:  { "id": "...", "name": "Blue Pen" }
 *   Error:    { "error": "Not found" }
 *   List:     [ {...}, {...} ]
 *
 * The frontend has to handle three different shapes.
 * Every API call needs custom parsing logic. Messy.
 *
 * With ApiResponse<T>:
 *   Success:  { "success": true,  "data": {...}, "message": "OK" }
 *   Error:    { "success": false, "data": null,  "message": "Not found" }
 *   List:     { "success": true,  "data": [...], "message": "OK" }
 *
 * The frontend has ONE shape to handle. One axios interceptor.
 * response.data.data for payload, response.data.message for errors.
 * Clean, predictable, professional.
 *
 * ──────────────────────────────────────────────────────────
 * @JsonInclude(NON_NULL) — WHY?
 * ──────────────────────────────────────────────────────────
 * Without it, a success response would serialize as:
 *   { "success": true, "message": "OK", "data": {...}, "errors": null }
 * With it:
 *   { "success": true, "message": "OK", "data": {...} }
 * Null fields are omitted. Cleaner JSON, smaller payload.
 *
 * ──────────────────────────────────────────────────────────
 * Generic type <T> — WHY?
 * ──────────────────────────────────────────────────────────
 * ApiResponse<ProductResponse>        → for single product
 * ApiResponse<Page<ProductSummaryResponse>> → for paginated list
 * ApiResponse<Void>                   → for delete (no body)
 * ApiResponse<AuthResponse>           → for login
 *
 * One class handles everything. No duplication.
 *
 * ──────────────────────────────────────────────────────────
 * Static factory methods (of, error) vs constructor:
 * ──────────────────────────────────────────────────────────
 * ApiResponse.of("Product created", product) reads better than
 * new ApiResponse<>(true, "Product created", product, null, Instant.now())
 * Factory methods hide construction complexity.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;
    private String message;
    private T data;
    private Instant timestamp;

    /**
     * Build a success response with data.
     * Usage: ApiResponse.of("Product fetched", productResponse)
     */
    public static <T> ApiResponse<T> of(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Build a success response with no data body (e.g., delete operations).
     * Usage: ApiResponse.ok("Product deleted")
     */
    public static <T> ApiResponse<T> ok(String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Build an error response.
     * Primarily used by GlobalExceptionHandler.
     * Usage: ApiResponse.error("Product not found")
     */
    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .timestamp(Instant.now())
                .build();
    }
}
