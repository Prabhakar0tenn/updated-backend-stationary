package com.stationary.stationary_backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Category — MongoDB document for the "categories" collection.
 *
 * ──────────────────────────────────────────────────────────
 * WHY Categories as a separate collection vs hardcoded?
 * ──────────────────────────────────────────────────────────
 * You approved this in Phase 1 review: "Admin-managed categories —
 * hardcoded list baad mein headache deta jab Sticky Notes add karni hoti."
 *
 * Correct. Hardcoded enums = code deployment for business decisions.
 * Database-driven categories = admin adds "Sticky Notes" in 30 seconds.
 *
 * ──────────────────────────────────────────────────────────
 * WHY isActive (boolean) here but ProductStatus (enum) on Product?
 * ──────────────────────────────────────────────────────────
 * Categories have only two states — visible or hidden.
 * There's no concept of "draft category" or "discontinued category".
 * A boolean is perfectly correct when there are truly only two states.
 * Products have more complexity (could add DRAFT, DISCONTINUED) → enum.
 * Don't over-engineer where simplicity is correct.
 *
 * ──────────────────────────────────────────────────────────
 * Relationship with Products:
 * ──────────────────────────────────────────────────────────
 * Products store: categoryId (String = Category._id)
 * Category doesn't know about products (no back-reference).
 * This is a one-to-many: one Category has many Products.
 *
 * When admin deactivates a category, you should also INACTIVE
 * all its products (or warn the admin). This is business logic
 * in CategoryService — not enforced at DB level.
 *
 * ──────────────────────────────────────────────────────────
 * imageUrl (optional):
 * ──────────────────────────────────────────────────────────
 * A category banner/icon shown in the storefront filter bar.
 * Example: a pen icon for "Pens" category.
 * Not required — admin can create categories without images.
 * Stored as a plain URL string (not ImageData) because:
 *   - We don't delete category images via Cloudinary often
 *   - Keeping it simple; can upgrade to ImageData in v2
 */
@Document("categories")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Category {

    @Id
    private String id;

    /**
     * Human-readable category name.
     * Example: "Pens", "Notebooks", "Sticky Notes"
     * unique=true: two categories with the same name makes no sense.
     */
    @Indexed(unique = true)
    private String name;

    /**
     * URL-safe identifier.
     * Example: "pens", "sticky-notes"
     * Used in: GET /api/v1/categories/sticky-notes
     * Used in: GET /api/v1/products?category=sticky-notes
     */
    @Indexed(unique = true)
    private String slug;

    /**
     * Optional URL for a category icon or banner image.
     * Displayed in the storefront's category filter sidebar.
     * If null, frontend shows a default placeholder icon.
     */
    private String imageUrl;

    /**
     * Soft delete flag for categories.
     * false = hidden from storefront, products still exist.
     * WHY not delete? A category with 50 products can't just
     * vanish — the products would be orphaned (categoryId pointing nowhere).
     * Admin flow: deactivate category → review products → reassign or INACTIVE them.
     *
     * Default is set in service layer, not here (same reasoning as Product.status).
     */
    @Indexed
    private Boolean isActive;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
