package com.stationary.stationary_backend.controller;

import com.stationary.stationary_backend.dto.request.ProductRequest;
import com.stationary.stationary_backend.dto.response.ApiResponse;
import com.stationary.stationary_backend.dto.response.ProductResponse;
import com.stationary.stationary_backend.dto.response.ProductSummaryResponse;
import com.stationary.stationary_backend.service.ImageService;
import com.stationary.stationary_backend.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * ProductController — REST endpoints for products.
 *
 * ──────────────────────────────────────────────────────────
 * PUBLIC endpoints (no auth required):
 * ──────────────────────────────────────────────────────────
 *   GET  /api/v1/products              — paginated list with filters
 *   GET  /api/v1/products/{slug}       — single product detail
 *
 * ──────────────────────────────────────────────────────────
 * ADMIN endpoints (Bearer token required):
 * ──────────────────────────────────────────────────────────
 *   GET    /api/v1/products/admin      — all products (incl INACTIVE), paginated
 *   POST   /api/v1/products            — create new product
 *   PUT    /api/v1/products/{id}       — update existing product (full update)
 *   PATCH  /api/v1/products/{id}/status — toggle ACTIVE/INACTIVE
 *   DELETE /api/v1/products/{id}       — soft delete (sets INACTIVE)
 *
 * ──────────────────────────────────────────────────────────
 * PAGINATION query params (GET /api/v1/products):
 * ──────────────────────────────────────────────────────────
 *   page      — 0-indexed page number (default: 0)
 *   size      — items per page (default: 12, max: 48)
 *   sort      — field: price | name | createdAt (default: createdAt)
 *   direction — asc | desc (default: desc)
 *   category  — category slug filter (optional)
 *   search    — full-text search term (optional)
 *   minPrice  — minimum price filter (optional)
 *   maxPrice  — maximum price filter (optional)
 *   inStock   — if true, only in-stock products (optional)
 *
 * ──────────────────────────────────────────────────────────
 * WHY @RequestParam with defaultValue?
 * ──────────────────────────────────────────────────────────
 * Without defaultValue, if the client doesn't send ?page=, Spring
 * throws a MissingServletRequestParameterException.
 * With defaultValue, missing params use the default — client can
 * call GET /api/v1/products and get page 0, size 12 automatically.
 *
 * ──────────────────────────────────────────────────────────
 * WHY /products/admin BEFORE /products/{slug}?
 * ──────────────────────────────────────────────────────────
 * Spring matches routes top-down within a controller.
 * If /{slug} was first, GET /products/admin would match it
 * with slug="admin" — wrong.
 * Static segments (/admin) must come BEFORE dynamic ones (/{slug}).
 */
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ImageService imageService;

    // ── PUBLIC ─────────────────────────────────────────────────────────────

    /**
     * Paginated product listing with optional filters.
     * PUBLIC — no auth required.
     * Response: Page<ProductSummaryResponse> (lightweight cards)
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<ProductSummaryResponse>>> getProducts(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(required = false) Boolean inStock
    ) {
        Page<ProductSummaryResponse> result = productService.getProducts(
                page, size, sort, direction, category, search, minPrice, maxPrice, inStock);
        return ResponseEntity.ok(ApiResponse.of("Products fetched successfully", result));
    }

    /**
     * Single product detail by URL slug.
     * PUBLIC — no auth required.
     * Response: ProductResponse (full detail with category + image + tags)
     *
     * IMPORTANT: This mapping MUST come after /admin to avoid slug="admin" conflict.
     */
    @GetMapping("/{slug}")
    public ResponseEntity<ApiResponse<ProductResponse>> getBySlug(
            @PathVariable String slug) {
        ProductResponse response = productService.getBySlug(slug);
        return ResponseEntity.ok(ApiResponse.of("Product fetched successfully", response));
    }

    // ── ADMIN ──────────────────────────────────────────────────────────────

    /**
     * All products for admin panel (ACTIVE + INACTIVE), paginated.
     * ADMIN only — SecurityConfig enforces this with .hasRole("ADMIN").
     *
     * WHY /admin path segment?
     * GET /products returns ACTIVE-only public listing.
     * GET /products/admin returns ALL products for management.
     * Different data contract → different paths. Clean and honest.
     */
    @GetMapping("/admin")
    public ResponseEntity<ApiResponse<Page<ProductSummaryResponse>>> getProductsForAdmin(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<ProductSummaryResponse> result = productService.getProductsForAdmin(page, size);
        return ResponseEntity.ok(ApiResponse.of("Products fetched for admin", result));
    }

    /**
     * Create a new product.
     * ADMIN only.
     * Body: ProductRequest (name, description, price, stock, categoryId, image, tags)
     * Returns: 201 Created with full ProductResponse
     *
     * WHY @Valid? Without it, @NotBlank / @Positive annotations on ProductRequest
     * are silently ignored. @Valid triggers Bean Validation before the method executes.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ProductResponse>> create(
            @Valid @RequestBody ProductRequest request) {
        ProductResponse response = productService.create(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.of("Product created successfully", response));
    }

    /**
     * Update an existing product (full update — PUT semantics).
     * ADMIN only.
     * If image.publicId changes, old Cloudinary image is deleted.
     *
     * WHY @AuthenticationPrincipal here?
     * Not used directly, but it's a good pattern to show — you CAN
     * get the authenticated admin's ID here for audit logging.
     * Removed for simplicity but keeping the pattern comment.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> update(
            @PathVariable String id,
            @Valid @RequestBody ProductRequest request) {
        ProductResponse response = productService.update(id, request, imageService);
        return ResponseEntity.ok(ApiResponse.of("Product updated successfully", response));
    }

    /**
     * Toggle product active/inactive status.
     * ADMIN only.
     * PATCH /api/v1/products/{id}/status
     *
     * WHY a separate endpoint for status toggle?
     * Keeps status change atomic and intentional.
     * Admin can't accidentally toggle status by editing a product's name.
     * Each action has one explicit endpoint.
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<ProductResponse>> toggleStatus(
            @PathVariable String id) {
        ProductResponse response = productService.toggleStatus(id);
        return ResponseEntity.ok(ApiResponse.of("Product status updated", response));
    }

    /**
     * Soft delete a product.
     * ADMIN only.
     * Sets status = INACTIVE (does NOT permanently remove from DB).
     * Deletes the Cloudinary image (permanent).
     * Returns: 200 with no data body.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        productService.delete(id, imageService);
        return ResponseEntity.ok(ApiResponse.ok("Product deleted successfully"));
    }
}
