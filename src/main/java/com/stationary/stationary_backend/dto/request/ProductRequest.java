package com.stationary.stationary_backend.dto.request;

import com.stationary.stationary_backend.dto.shared.ImageDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

/**
 * ProductRequest — What the admin sends to create/update a product.
 * Used for: POST /api/v1/products, PUT /api/v1/products/{id}
 *
 * ──────────────────────────────────────────────────────────
 * FULL version (replaces the stub from earlier).
 * ProductMapper's @Mapping(target = "image/tags", ignore = true)
 * annotations should be removed now that these fields exist here.
 * (We'll update the mapper after this.)
 * ──────────────────────────────────────────────────────────
 *
 * ──────────────────────────────────────────────────────────
 * WHY @Valid on the ImageDto field?
 * ──────────────────────────────────────────────────────────
 * @Valid triggers "cascaded validation" — it also runs
 * validation annotations inside the nested ImageDto class.
 * Without @Valid, @NotBlank inside ImageDto is silently ignored.
 *
 * Rule: whenever a DTO contains another object (not a primitive),
 * annotate it with @Valid to cascade validation down.
 *
 * ──────────────────────────────────────────────────────────
 * WHY no 'slug' field here?
 * ──────────────────────────────────────────────────────────
 * Slug is auto-generated from name in ProductService.
 * The admin should not be able to set arbitrary slugs —
 * that could create URL conflicts or injection.
 * Slug generation is a server-side concern, not client input.
 *
 * ──────────────────────────────────────────────────────────
 * WHY no 'status' field here?
 * ──────────────────────────────────────────────────────────
 * Status is controlled via a dedicated endpoint:
 *   PATCH /api/v1/products/{id}/status
 * Mixing status into the create/update request creates ambiguity:
 * "Was this product intentionally created as INACTIVE?"
 * Separating concerns makes the API honest.
 *
 * ──────────────────────────────────────────────────────────
 * Double for price (repeat warning):
 * ──────────────────────────────────────────────────────────
 * @Positive ensures price > 0. @DecimalMin ensures >= 0 if you
 * want to allow free products. @Positive is correct here
 * (stationery should cost something).
 *
 * REMINDER: Never do arithmetic on this Double in v1.
 */
@Data
public class ProductRequest {

    @NotBlank(message = "Product name is required")
    @Size(max = 200, message = "Product name must not exceed 200 characters")
    private String name;

    // Optional — not every product needs a description
    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    private String description;

    @NotNull(message = "Price is required")
    @Positive(message = "Price must be greater than 0")
    private Double price;

    /**
     * Optional MRP/original price for discount display.
     * If provided, must be > price (validated in service layer).
     * If null/absent, no discount is shown on frontend.
     * Backward compatible: existing products without this field work fine.
     */
    @Positive(message = "Original price must be greater than 0")
    private Double originalPrice;

    @NotNull(message = "Stock is required")
    @Min(value = 0, message = "Stock cannot be negative")
    private Integer stock;

    @NotBlank(message = "Category ID is required")
    private String categoryId;

    /**
     * Image metadata from Cloudinary (uploaded before this request).
     * Admin flow:
     *   1. Upload image → POST /api/v1/images/upload → get {publicId, url}
     *   2. Use those values in this field when creating/updating product
     *
     * @Valid cascades validation into ImageDto (checks @NotBlank inside it).
     */
    @NotNull(message = "Product image is required")
    @Valid
    private ImageDto image;

    /**
     * Optional search tags. Admin-curated keywords.
     * Example: ["pen", "gel", "blue", "writing"]
     * Frontend doesn't show these — they're only for search indexing.
     */
    private List<
            @NotBlank(message = "Tags cannot be blank strings")
            @Size(max = 50, message = "Each tag must be 50 characters or less")
                    String> tags;
}
