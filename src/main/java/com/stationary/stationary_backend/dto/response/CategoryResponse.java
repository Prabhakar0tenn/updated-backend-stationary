package com.stationary.stationary_backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * CategoryResponse — Full category detail.
 * Used in: ProductResponse.category (nested)
 *          GET /api/v1/categories listing
 *          GET /api/v1/categories/{slug} detail
 *
 * ──────────────────────────────────────────────────────────
 * isActive field — WHY expose it publicly?
 * ──────────────────────────────────────────────────────────
 * Public listing only returns isActive=true categories anyway.
 * But the admin panel shows all categories including inactive ones.
 * Including isActive in the response lets the admin UI
 * show a "disabled" badge without another API call.
 *
 * ──────────────────────────────────────────────────────────
 * createdAt / updatedAt — WHY in category response?
 * ──────────────────────────────────────────────────────────
 * Admin dashboard usefulness: "When was 'Sticky Notes' added?"
 * Public listing doesn't show these — @JsonInclude(NON_NULL)
 * on ApiResponse handles null suppression if needed.
 * Simpler to include them and let the frontend ignore what it doesn't need.
 */
@Data
@Builder
public class CategoryResponse {

    private String id;
    private String name;
    private String slug;
    private String imageUrl;
    private Boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;
}
