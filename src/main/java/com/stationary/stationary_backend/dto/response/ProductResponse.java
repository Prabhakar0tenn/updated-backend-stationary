package com.stationary.stationary_backend.dto.response;

import com.stationary.stationary_backend.dto.shared.ImageDto;
import com.stationary.stationary_backend.model.enums.ProductStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * ProductResponse — Full product detail returned by GET /api/v1/products/{slug}
 *
 * ──────────────────────────────────────────────────────────
 * WHY return a DTO and not the Product entity directly?
 * ──────────────────────────────────────────────────────────
 * This is the #1 mistake in beginner Spring Boot code.
 * If you return Product entity directly from a controller:
 *
 *   1. ALL fields serialize to JSON — including internal fields
 *      (updatedAt, status, categoryId) that the frontend doesn't need.
 *
 *   2. If you add a field to Product for internal use (e.g., a flag
 *      for analytics), it immediately leaks to the public API.
 *
 *   3. MongoDB ObjectId fields, @CreatedDate fields, etc. may serialize
 *      unexpectedly and expose implementation details.
 *
 *   4. You lose control over the API contract. The API shape becomes
 *      "whatever the entity looks like" — brittle.
 *
 * With a DTO, you decide EXACTLY what the API returns.
 * API contract is stable even if the entity changes internally.
 *
 * ──────────────────────────────────────────────────────────
 * WHAT'S IN THIS RESPONSE vs THE ENTITY:
 * ──────────────────────────────────────────────────────────
 * Entity field       → Response field    → Notes
 * id                 → id                → kept (frontend needs it for admin actions)
 * name               → name              → kept
 * slug               → slug              → kept (canonical URL identifier)
 * description        → description       → kept
 * price              → price             → kept
 * stock              → stock             → kept (frontend shows "in stock" badge)
 * status             → status            → kept (frontend can grey out INACTIVE)
 * categoryId (String)→ category (obj)    → EXPANDED: fetched and mapped to CategoryResponse
 * image (ImageData)  → image (ImageDto)  → mapped from embedded doc to DTO
 * tags               → tags              → kept
 * createdAt          → createdAt         → kept (admin: "added 3 days ago")
 * updatedAt          → updatedAt         → kept (admin: "last modified")
 *
 * ──────────────────────────────────────────────────────────
 * category as nested object (not just categoryId):
 * ──────────────────────────────────────────────────────────
 * Frontend product detail page shows: "Category: Pens"
 * If we return just categoryId, frontend needs ANOTHER API call.
 * Returning the full CategoryResponse avoids that round trip.
 * This is called "eager loading" — trade slightly larger payload
 * for fewer HTTP requests. For a product detail page, correct tradeoff.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductResponse {

    private String id;
    private String name;
    private String slug;
    private String description;
    private Double price;
    /**
     * MRP / struck-through price. Null if no discount applies.
     * discountAmount and discountPercentage are also null in that case.
     */
    private Double originalPrice;
    /** originalPrice - price. Null when originalPrice is absent. */
    private Double discountAmount;
    /** Round percentage: (discountAmount / originalPrice) * 100. Null when not applicable. */
    private Double discountPercentage;
    private Integer stock;
    private ProductStatus status;
    private CategoryResponse category;  // expanded, not just categoryId
    private ImageDto image;
    private List<String> tags;
    private Instant createdAt;
    private Instant updatedAt;
}
