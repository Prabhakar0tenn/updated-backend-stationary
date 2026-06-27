package com.stationary.stationary_backend.controller;

import com.stationary.stationary_backend.dto.request.CategoryRequest;
import com.stationary.stationary_backend.dto.response.ApiResponse;
import com.stationary.stationary_backend.dto.response.CategoryResponse;
import com.stationary.stationary_backend.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * CategoryController — REST endpoints for categories.
 *
 * ──────────────────────────────────────────────────────────
 * PUBLIC endpoints:
 * ──────────────────────────────────────────────────────────
 *   GET /api/v1/categories             — all ACTIVE categories (for storefront filter)
 *   GET /api/v1/categories/{slug}      — single active category by slug
 *
 * ──────────────────────────────────────────────────────────
 * ADMIN endpoints:
 * ──────────────────────────────────────────────────────────
 *   GET    /api/v1/categories/admin    — all categories incl INACTIVE
 *   POST   /api/v1/categories          — create new category
 *   PUT    /api/v1/categories/{id}     — update category
 *   PATCH  /api/v1/categories/{id}/status — toggle active/inactive
 *   DELETE /api/v1/categories/{id}     — hard delete (only if no products)
 *
 * ──────────────────────────────────────────────────────────
 * NOTE: /admin MUST be mapped before /{slug} in any request matching.
 * Spring's @GetMapping("/admin") is a static path and takes priority
 * over @GetMapping("/{slug}") for GET /categories/admin requests.
 * Within a single controller, static paths always win over dynamic ones.
 */
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    // ── PUBLIC ─────────────────────────────────────────────────────────────

    /**
     * All active categories — used by storefront filter sidebar.
     * PUBLIC. Cached 60 min in Redis.
     * Response: List<CategoryResponse>
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getAll() {
        List<CategoryResponse> categories = categoryService.getAll();
        return ResponseEntity.ok(ApiResponse.of("Categories fetched successfully", categories));
    }

    /**
     * Single active category by slug.
     * PUBLIC. Used for category page breadcrumb/metadata.
     */
    @GetMapping("/{slug}")
    public ResponseEntity<ApiResponse<CategoryResponse>> getBySlug(
            @PathVariable String slug) {
        CategoryResponse response = categoryService.getBySlug(slug);
        return ResponseEntity.ok(ApiResponse.of("Category fetched successfully", response));
    }

    // ── ADMIN ──────────────────────────────────────────────────────────────

    /**
     * All categories for admin panel (includes inactive).
     * ADMIN only.
     */
    @GetMapping("/admin")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getAllForAdmin() {
        List<CategoryResponse> categories = categoryService.getAllForAdmin();
        return ResponseEntity.ok(ApiResponse.of("All categories fetched for admin", categories));
    }

    /**
     * Create a new category.
     * ADMIN only.
     * Body: { "name": "Sticky Notes", "imageUrl": "https://..." }
     * Returns: 201 with CategoryResponse
     */
    @PostMapping
    public ResponseEntity<ApiResponse<CategoryResponse>> create(
            @Valid @RequestBody CategoryRequest request) {
        CategoryResponse response = categoryService.create(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.of("Category created successfully", response));
    }

    /**
     * Update category name/image.
     * ADMIN only.
     * Slug is regenerated if name changes.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CategoryResponse>> update(
            @PathVariable String id,
            @Valid @RequestBody CategoryRequest request) {
        CategoryResponse response = categoryService.update(id, request);
        return ResponseEntity.ok(ApiResponse.of("Category updated successfully", response));
    }

    /**
     * Toggle category active/inactive.
     * ADMIN only.
     * Guard: cannot deactivate if active products exist under this category.
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<CategoryResponse>> toggleStatus(
            @PathVariable String id) {
        CategoryResponse response = categoryService.toggleStatus(id);
        return ResponseEntity.ok(ApiResponse.of("Category status updated", response));
    }

    /**
     * Permanently delete a category.
     * ADMIN only.
     * Guard: cannot delete if any products (active or inactive) exist in this category.
     * Returns: 200 with no data body.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        categoryService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Category deleted successfully"));
    }
}
