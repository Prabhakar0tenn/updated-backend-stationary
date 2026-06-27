package com.stationary.stationary_backend.model;

import com.stationary.stationary_backend.model.embedded.ImageData;
import com.stationary.stationary_backend.model.enums.ProductStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.List;

/**
 * Product — MongoDB document mapping the "products" collection.
 *
 * ──────────────────────────────────────────────────────────
 * @Document("products") — WHY plural?
 * ──────────────────────────────────────────────────────────
 * MongoDB convention: collection names are plural lowercase.
 * The class name is singular (Product = one product).
 * The collection stores many → plural (products).
 * Leaving this off uses the class name as-is → "Product"
 * (capital P, singular) — ugly and non-standard.
 *
 * ──────────────────────────────────────────────────────────
 * @Data vs @Getter + @Setter separately
 * ──────────────────────────────────────────────────────────
 * @Data = @Getter + @Setter + @ToString + @EqualsAndHashCode
 * For entities, @Data is fine. One caveat:
 * @EqualsAndHashCode on entities with @Id can cause issues
 * in Sets/Maps if the ID is null before save. For this app,
 * products are never put in Sets, so @Data is clean.
 *
 * ──────────────────────────────────────────────────────────
 * INDEXES — WHY each one exists:
 * ──────────────────────────────────────────────────────────
 * Without indexes, MongoDB does a FULL COLLECTION SCAN on
 * every query — reads every document to find matches.
 * With 10 products: fine. With 10,000: slow. With 1M: broken.
 *
 * Indexes are a sorted B-tree that MongoDB maintains alongside
 * the collection. Query → index → jump directly to matching docs.
 *
 * We create indexes for every field that appears in:
 *   - WHERE clauses (status, categoryId)
 *   - ORDER BY (createdAt, price)
 *   - Unique lookups (slug)
 *   - Text search (name, tags)
 *
 * ──────────────────────────────────────────────────────────
 * @CreatedDate / @LastModifiedDate — WHY use these?
 * ──────────────────────────────────────────────────────────
 * Instead of manually doing:
 *   product.setCreatedAt(Instant.now());
 * in every service method, Spring Data MongoDB fills these
 * automatically when you save the document.
 *
 * REQUIRES: @EnableMongoAuditing on a @Configuration class.
 * We add that to MongoConfig (created below).
 *
 * ──────────────────────────────────────────────────────────
 * WHY Instant over LocalDateTime?
 * ──────────────────────────────────────────────────────────
 * LocalDateTime has NO timezone info. If your server is in
 * UTC+5:30 and MongoDB stores "2025-05-30T23:00:00", you don't
 * know what timezone that is.
 * Instant is always UTC. Unambiguous. Serializes to ISO-8601.
 * Always use Instant for timestamps in backend systems.
 */
@Document("products")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@CompoundIndex(name = "status_category_idx", def = "{'status': 1, 'categoryId': 1}")
// WHY compound index? Queries like: WHERE status='ACTIVE' AND categoryId='...'
// A compound index serves both the status filter AND the category filter
// in one index lookup. Two separate indexes would require MongoDB to intersect
// two result sets — less efficient.
public class Product {

    /**
     * MongoDB auto-generated primary key.
     * Stored as ObjectId in MongoDB, mapped to String in Java.
     * WHY String over ObjectId type? String works everywhere —
     * JSON, path variables, query params — without conversion.
     */
    @Id
    private String id;

    /**
     * Display name of the product.
     * Example: "Blue Gel Pen"
     */
    private String name;

    /**
     * URL-safe identifier for this product.
     * Derived from name: "Blue Gel Pen" → "blue-gel-pen"
     * Used in: GET /api/v1/products/blue-gel-pen
     *
     * WHY unique=true?
     * Slug is used in URLs. Two products with the same slug
     * would make one unreachable. Uniqueness enforced at DB level
     * (not just application level) → bulletproof even under concurrent writes.
     *
     * WHY at DB level AND application level?
     * Application level: gives a friendly error message.
     * DB level: last line of defense if two requests race simultaneously.
     */
    @Indexed(unique = true)
    private String slug;

    /**
     * Marketing description shown on product detail page.
     * Optional — some products (e.g., commodity pens) don't need one.
     */
    private String description;

    /**
     * Price in Indian Rupees. Stored as Double.
     * WHY Double and not BigDecimal?
     * Decision from Phase 1: stationery prices are round numbers.
     * No arithmetic is done on this field — display only.
     * ⚠️ If you ever add discounts or GST calculation in v2,
     * migrate this to BigDecimal before doing the math.
     *
     * @Field("price") — redundant here (field name matches),
     * but shown as example of how to rename fields in MongoDB
     * without renaming the Java property.
     */
    @Field("price")
    private Double price;

    /**
     * Original / MRP price shown as struck-through on the frontend.
     * Optional — if null, no discount is displayed.
     * Must be greater than price (validated in DTO layer).
     *
     * Example: originalPrice=80, price=60 → discountAmount=20
     * discountAmount and discountPercentage are NOT stored here;
     * they are computed on-the-fly in the service/response layer.
     */
    @Field("originalPrice")
    private Double originalPrice;

    /**
     * Current inventory count.
     * 0 = out of stock (still visible, just can't order).
     * <0 is not allowed — enforced via @Min(0) on the DTO, not here.
     * WHY not enforce on entity? Entities don't validate themselves.
     * Validation is the DTO's job. Entity trusts what service gives it.
     */
    private Integer stock;

    /**
     * Reference to the Category document's _id.
     * WHY String (not @DBRef Category)?
     *
     * @DBRef causes MongoDB to do a SECOND QUERY to fetch the
     * referenced document every time you load a Product.
     * That's N+1 problem territory.
     *
     * Instead, we store just the categoryId. When we need category
     * name for the response DTO, we fetch it once in the service
     * and map it. Controlled, explicit, efficient.
     *
     * FUTURE: If you need category data alongside products frequently,
     * consider denormalizing category name into the product document
     * ("categoryName": "Pens") so you never need the join.
     */
    @Indexed
    private String categoryId;

    /**
     * Product image metadata (Cloudinary publicId + URL).
     * Single image for v1. Architecture note: upgrading to
     * List<ImageData> in v2 only changes this one field.
     */
    private ImageData image;

    /**
     * Soft-delete / visibility status.
     * ACTIVE = visible to public. INACTIVE = hidden.
     * Default ACTIVE set in service layer (ProductService.create),
     * not here — entity shouldn't have business logic defaults.
     *
     * WHY not here? If you put @Builder.Default here, you're
     * mixing persistence concerns with business rules.
     * The service decides what status a new product gets.
     */
    @Indexed
    private ProductStatus status;

    /**
     * Search tags for full-text search.
     * Example: ["pen", "gel", "blue", "writing", "smooth"]
     *
     * @TextIndexed tells MongoDB to include this field in the
     * text search index alongside 'name'. Both fields are
     * searched when you run a $text query.
     *
     * WHY tags separately from description?
     * Descriptions can be long. Text search on long strings is
     * noisier. Tags are admin-curated, precise search terms.
     */
    @TextIndexed
    private List<String> tags;

    /**
     * Auto-set on first save. Never updated after that.
     * Requires @EnableMongoAuditing.
     */
    @CreatedDate
    private Instant createdAt;

    /**
     * Auto-updated on every save.
     * Used to show "last updated" on admin dashboard.
     * Also useful for cache invalidation decisions.
     */
    @LastModifiedDate
    private Instant updatedAt;
}
