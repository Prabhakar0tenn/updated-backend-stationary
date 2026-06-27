package com.stationary.stationary_backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * CategoryRequest — What the admin sends to create/update a category.
 * Used for: POST /api/v1/categories, PUT /api/v1/categories/{id}
 *
 * ──────────────────────────────────────────────────────────
 * WHY no 'slug' field?
 * ──────────────────────────────────────────────────────────
 * Same as ProductRequest — slug is auto-generated from name.
 * "Sticky Notes" → "sticky-notes"
 *
 * ──────────────────────────────────────────────────────────
 * WHY no 'isActive' field?
 * ──────────────────────────────────────────────────────────
 * Status toggling is a separate operation:
 *   PATCH /api/v1/categories/{id}/status
 * This is a deliberate UI choice — admin should make an explicit
 * "toggle status" action, not accidentally flip it via a PUT.
 *
 * ──────────────────────────────────────────────────────────
 * imageUrl is a URL string, not an ImageDto:
 * ──────────────────────────────────────────────────────────
 * Category images are optional and simpler — just a URL.
 * Admin can paste an existing URL or upload one separately.
 * We don't need publicId for category images in v1
 * (we're not programmatically deleting them via Cloudinary).
 */
@Data
public class CategoryRequest {

    @NotBlank(message = "Category name is required")
    @Size(max = 100, message = "Category name must not exceed 100 characters")
    private String name;

    // Optional — URL from Cloudinary or external source
    private String imageUrl;
}
