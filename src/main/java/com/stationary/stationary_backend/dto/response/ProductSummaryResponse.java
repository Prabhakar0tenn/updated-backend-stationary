package com.stationary.stationary_backend.dto.response;

import com.stationary.stationary_backend.dto.shared.ImageDto;
import com.stationary.stationary_backend.model.enums.ProductStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

/**
 * ProductSummaryResponse — Lightweight product for list pages.
 * Returned by: GET /api/v1/products (paginated listing)
 *
 * ──────────────────────────────────────────────────────────
 * WHY a separate "Summary" DTO instead of reusing ProductResponse?
 * ──────────────────────────────────────────────────────────
 * The product listing page shows a grid of product cards.
 * Each card only needs: name, slug, price, stock, image, categoryName.
 *
 * If you return full ProductResponse for each item:
 *   - description (up to 2000 chars) × 24 products = 48,000 chars wasted
 *   - tags × 24 = unnecessary data
 *   - Full CategoryResponse nested × 24 = just categoryName needed
 *   - timestamps × 24 = not shown on cards
 *
 * Payload reduction: ~60-70% smaller. Faster load. Less bandwidth.
 * On mobile in India with slower connections, this matters.
 *
 * ──────────────────────────────────────────────────────────
 * WHEN does frontend upgrade to full ProductResponse?
 * ──────────────────────────────────────────────────────────
 * User clicks a product card → GET /api/v1/products/{slug}
 * That single endpoint returns the full ProductResponse.
 * The list gave you enough to render the card.
 * The detail gives you everything for the product page.
 *
 * This is the "list/detail" API pattern — industry standard.
 *
 * ──────────────────────────────────────────────────────────
 * categoryName as String (not CategoryResponse):
 * ──────────────────────────────────────────────────────────
 * Product cards show "Pens" label, not a category object.
 * Returning a full CategoryResponse (with slug, imageUrl, timestamps)
 * for each of 24 products is overkill.
 * Just the name is enough for the card label.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductSummaryResponse {

    private String id;
    private String name;
    private String slug;
    private Double price;
    /** MRP / struck-through price. Null when no discount. */
    private Double originalPrice;
    /** originalPrice - price. Null when not applicable. */
    private Double discountAmount;
    /** Discount percentage rounded to 1 decimal. Null when not applicable. */
    private Double discountPercentage;
    private Integer stock;
    private ProductStatus status;
    private ImageDto image;
    private String categoryName;  // just the name — not full CategoryResponse
}
