package com.stationary.stationary_backend.repository;

import com.stationary.stationary_backend.model.Category;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

/**
 * CategoryRepository — Data access layer for Categories.
 *
 * ──────────────────────────────────────────────────────────
 * HOW public queries use categories:
 * ──────────────────────────────────────────────────────────
 * Public storefront:
 *   GET /api/v1/categories       → findByIsActiveTrue()
 *   GET /api/v1/categories/{slug} → findBySlugAndIsActiveTrue()
 *   GET /api/v1/products?category=pens → slug used to find categoryId,
 *                                        then products filtered by categoryId
 *
 * Admin panel:
 *   GET /api/v1/categories (admin) → findAll() (includes inactive)
 *   Before deactivating a category → countProductsInCategory via ProductRepository
 *
 * ──────────────────────────────────────────────────────────
 * WHY findByIsActiveTrue() instead of findByIsActive(true)?
 * ──────────────────────────────────────────────────────────
 * Both work. Spring Data understands both.
 * findByIsActiveTrue() is more readable — reads like English.
 * findByIsActive(Boolean active) is more flexible — caller controls value.
 *
 * We use the readable version for the common case (active only).
 * We use the flexible version when we need to pass the value dynamically.
 *
 * ──────────────────────────────────────────────────────────
 * SLUG-BASED LOOKUP — WHY not ID?
 * ──────────────────────────────────────────────────────────
 * Product listing uses: GET /api/v1/products?category=pens
 * "pens" is the category slug — human-readable, URL-safe.
 * This means every product category filter starts with a slug lookup.
 * The slug index on Category makes this O(log n) — fast.
 *
 * Alternative: use category ID in the URL parameter.
 * Problem: MongoDB ObjectIds are ugly and non-bookmarkable.
 * Slug keeps URLs clean: ?category=pens vs ?category=64f2a...
 *
 * ──────────────────────────────────────────────────────────
 * FUTURE (v2):
 * ──────────────────────────────────────────────────────────
 * If category hierarchy is needed (parent/child categories),
 * add: Optional<Category> findByParentIdAndIsActiveTrue(String parentId)
 * The Category model gets a parentId field. This repository barely changes.
 */
public interface CategoryRepository extends MongoRepository<Category, String> {

    /**
     * All active categories for public storefront display.
     * → db.categories.find({ isActive: true })
     */
    List<Category> findByIsActiveTrue();

    /**
     * All categories regardless of status — for admin listing.
     * Inherited findAll() from MongoRepository serves this.
     * No custom method needed.
     */

    /**
     * Single active category by slug — for public category detail page
     * and for resolving category slug → categoryId in product queries.
     * → db.categories.find({ slug: "pens", isActive: true })
     */
    Optional<Category> findBySlugAndIsActiveTrue(String slug);

    /**
     * Find by slug regardless of status — for admin operations
     * and for uniqueness check when creating/updating a category.
     * → db.categories.find({ slug: "pens" })
     */
    Optional<Category> findBySlug(String slug);

    /**
     * Check if a category name already exists (case-sensitive).
     * Used before creating a category to give a meaningful error:
     * "Category 'Pens' already exists" instead of MongoDB duplicate key error.
     *
     * WHY not rely on MongoDB's duplicate key exception?
     * You can, but the error message is ugly and implementation-specific.
     * Pre-checking gives you control over the error message and status code.
     * → db.categories.find({ name: "Pens" })
     */
    boolean existsByName(String name);

    /**
     * Check if a slug already exists — used by slug uniqueness validation.
     * Also used by slug collision resolution for categories.
     * → db.categories.find({ slug: "pens" }) → count > 0
     */
    boolean existsBySlug(String slug);
}
