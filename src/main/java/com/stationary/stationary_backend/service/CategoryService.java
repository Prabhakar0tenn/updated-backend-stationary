package com.stationary.stationary_backend.service;

import com.stationary.stationary_backend.dto.request.CategoryRequest;
import com.stationary.stationary_backend.dto.response.CategoryResponse;
import com.stationary.stationary_backend.exception.BadRequestException;
import com.stationary.stationary_backend.exception.DuplicateResourceException;
import com.stationary.stationary_backend.exception.ResourceNotFoundException;
import com.stationary.stationary_backend.mapper.CategoryMapper;
import com.stationary.stationary_backend.model.Category;
import com.stationary.stationary_backend.repository.CategoryRepository;
import com.stationary.stationary_backend.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * CategoryService — Business logic for Category CRUD.
 *
 * ──────────────────────────────────────────────────────────
 * CACHING STRATEGY:
 * ──────────────────────────────────────────────────────────
 * "categories" cache → full list → TTL 60 min (categories rarely change).
 *
 * @Cacheable("categories") on getAll() → Spring checks Redis first.
 *   Cache hit  → return cached list, NO DB query.
 *   Cache miss → query DB, store result in Redis, return.
 *
 * @CacheEvict(allEntries = true) on create/update/delete → clears the
 * ENTIRE "categories" cache. WHY allEntries? The cached key is the
 * full list — one eviction key invalidates it all.
 *
 * ──────────────────────────────────────────────────────────
 * SLUG GENERATION:
 * ──────────────────────────────────────────────────────────
 * "Sticky Notes" → "sticky-notes"
 * Rules:
 *   1. Lowercase
 *   2. Remove accents/diacritics (é → e)
 *   3. Replace spaces and special chars with hyphens
 *   4. Collapse consecutive hyphens
 *   5. Strip leading/trailing hyphens
 *
 * Uniqueness: DB has unique index on slug. If collision, throw DuplicateResourceException.
 * Categories rarely collide — no suffix numbering needed (unlike products).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CategoryMapper categoryMapper;

    // ── Public (READ) ──────────────────────────────────────────────────────

    /**
     * Get all ACTIVE categories (public storefront).
     * Cached for 60 minutes — categories change very infrequently.
     *
     * WHY key = "'all'"?
     * Spring Cache key is the method argument by default.
     * getAll() has no arguments → key would be empty.
     * Explicit literal 'all' gives us a predictable, readable Redis key:
     *   "categories::all"
     */
    @Cacheable(value = "categories", key = "'all'")
    public List<CategoryResponse> getAll() {
        log.debug("Cache miss — fetching all active categories from DB");
        return categoryRepository.findByIsActiveTrue()
                .stream()
                .map(categoryMapper::toResponse)
                .toList();
    }

    /**
     * Get a single category by slug (public).
     * Used for category filter pages: /category/pens
     */
    public CategoryResponse getBySlug(String slug) {
        Category category = categoryRepository.findBySlugAndIsActiveTrue(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "slug", slug));
        return categoryMapper.toResponse(category);
    }

    // ── Admin (READ) ───────────────────────────────────────────────────────

    /**
     * Get ALL categories including inactive ones (admin panel only).
     * WHY not cacheable? Admin needs real-time view of all categories.
     * This endpoint is called by admin, not every visitor — DB hit is fine.
     */
    public List<CategoryResponse> getAllForAdmin() {
        return categoryRepository.findAll()
                .stream()
                .map(categoryMapper::toResponse)
                .toList();
    }

    // ── Admin (WRITE) ──────────────────────────────────────────────────────

    /**
     * Create a new category.
     *
     * @CacheEvict → clear "categories" cache so public listing is refreshed.
     * allEntries=true → the cached key is the full list — evict the whole cache name.
     */
    @CacheEvict(value = "categories", allEntries = true)
    public CategoryResponse create(CategoryRequest request) {
        String slug = generateSlug(request.getName());

        // Business rule: slug uniqueness check at application level
        // (DB index is the final guard — this gives a friendly error message)
        if (categoryRepository.existsBySlug(slug)) {
            throw new DuplicateResourceException("Category", "name", request.getName());
        }

        Category category = categoryMapper.toEntity(request);
        category.setSlug(slug);
        category.setIsActive(true);     // new categories are active by default

        Category saved = categoryRepository.save(category);
        log.info("Category created: '{}' (slug: {})", saved.getName(), saved.getSlug());
        return categoryMapper.toResponse(saved);
    }

    /**
     * Update category name and/or imageUrl.
     * If name changed, slug is regenerated.
     *
     * @Caching: evict both the full list AND any individual cached entry
     */
    @CacheEvict(value = "categories", allEntries = true)
    public CategoryResponse update(String id, CategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", id));

        // Slug regeneration: only if name actually changed
        boolean nameChanged = !category.getName().equals(request.getName());
        if (nameChanged) {
            String newSlug = generateSlug(request.getName());
            // Check new slug doesn't conflict with another category
            categoryRepository.findBySlug(newSlug).ifPresent(existing -> {
                if (!existing.getId().equals(id)) {  // not the same category
                    throw new DuplicateResourceException("Category", "name", request.getName());
                }
            });
            category.setSlug(newSlug);
        }

        categoryMapper.updateCategoryFromDto(request, category);
        Category saved = categoryRepository.save(category);
        log.info("Category updated: '{}'", saved.getName());
        return categoryMapper.toResponse(saved);
    }

    /**
     * Toggle category active/inactive status.
     *
     * BUSINESS RULE: Cannot deactivate a category that still has active products.
     * WHY? Active products in an inactive category are orphaned in the UI —
     * they show under a category that doesn't appear in the filter.
     * Admin must INACTIVE the products first (or reassign them).
     */
    @CacheEvict(value = "categories", allEntries = true)
    public CategoryResponse toggleStatus(String id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", id));

        // Guard: don't deactivate if products still active under this category
        boolean isDeactivating = Boolean.TRUE.equals(category.getIsActive());
        if (isDeactivating) {
            long activeProductCount = productRepository.countByCategoryIdAndStatus(
                    id, com.stationary.stationary_backend.model.enums.ProductStatus.ACTIVE);
            if (activeProductCount > 0) {
                throw new BadRequestException(
                        String.format("Cannot deactivate category '%s' — it has %d active product(s). " +
                                "Deactivate or reassign products first.", category.getName(), activeProductCount));
            }
        }

        category.setIsActive(!category.getIsActive());
        Category saved = categoryRepository.save(category);
        log.info("Category '{}' status toggled to: {}", saved.getName(), saved.getIsActive());
        return categoryMapper.toResponse(saved);
    }

    /**
     * Permanently delete a category.
     *
     * BUSINESS RULE: Cannot delete a category that has ANY products (active or inactive).
     * Deleting would orphan those products. Admin must reassign or delete products first.
     *
     * WHY hard delete for categories (unlike soft delete for products)?
     * Products soft-delete because the URL (slug) must remain reserved.
     * Categories are referenced by categoryId in products — if a category is
     * fully deleted with no products, the reference is cleaned up.
     * A category with no products is safe to delete permanently.
     */
    @CacheEvict(value = "categories", allEntries = true)
    public void delete(String id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", id));

        long totalProductCount = productRepository.findByCategoryId(id).size();
        if (totalProductCount > 0) {
            throw new BadRequestException(
                    String.format("Cannot delete category '%s' — it has %d product(s). " +
                            "Reassign or delete all products first.", category.getName(), totalProductCount));
        }

        categoryRepository.delete(category);
        log.info("Category '{}' deleted permanently", category.getName());
    }

    // ── Slug utility (package-private so ProductService can reuse) ──────────

    /**
     * Converts a human-readable name to a URL-safe slug.
     * "Sticky Notes & Pads" → "sticky-notes-pads"
     *
     * WHY Normalizer.normalize()?
     * Handles accented characters: "Léon" → "Leon" → "leon"
     * NFD (Canonical Decomposition) splits accented chars into base + accent mark.
     * The regex [^\\p{ASCII}] then removes the accent marks.
     */
    public String generateSlug(String name) {
        return Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("[^\\p{ASCII}]", "")  // remove non-ASCII (accent marks)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s-]", "")  // keep only alphanumeric, spaces, hyphens
                .trim()
                .replaceAll("\\s+", "-")           // spaces → hyphens
                .replaceAll("-+", "-");             // collapse multiple hyphens
    }
}
