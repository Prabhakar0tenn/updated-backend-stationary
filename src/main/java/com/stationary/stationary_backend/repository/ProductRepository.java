package com.stationary.stationary_backend.repository;

import com.stationary.stationary_backend.model.Product;
import com.stationary.stationary_backend.model.enums.ProductStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

/**
 * ProductRepository — Data access layer for Products.
 *
 * ──────────────────────────────────────────────────────────
 * WHY extend MongoRepository<Product, String>?
 * ──────────────────────────────────────────────────────────
 * MongoRepository gives you for FREE:
 *   save(), findById(), findAll(), deleteById(), count(), exists()...
 * You don't write a single line of MongoDB query code for these.
 * Spring Data generates the implementation at startup.
 *
 * The two type params: <Product, String>
 *   Product = the document class
 *   String  = the type of @Id (we use String, not ObjectId)
 *
 * ──────────────────────────────────────────────────────────
 * Spring Data Derived Query Methods — HOW they work:
 * ──────────────────────────────────────────────────────────
 * Spring reads the METHOD NAME and generates the MongoDB query.
 *
 * findByStatus(ProductStatus.ACTIVE)
 *   → db.products.find({ status: "ACTIVE" })
 *
 * findBySlugAndStatus("blue-pen", ACTIVE)
 *   → db.products.find({ slug: "blue-pen", status: "ACTIVE" })
 *
 * Rule: findBy{FieldName}And{FieldName}(param1, param2)
 * Spring parses the camelCase field names from your entity.
 *
 * WHY is this safe? Because it reads field names from the entity.
 * If you rename "status" to "visibility", the compiler tells you
 * this method name is wrong. No silent runtime SQL injection risk.
 *
 * ──────────────────────────────────────────────────────────
 * IMPORTANT CHANGE from your original code:
 * ──────────────────────────────────────────────────────────
 * BEFORE: findByDeletedFalse() — used boolean deleted field
 * AFTER:  findByStatus(ProductStatus) — uses enum status field
 *
 * WHY the change? We replaced `boolean deleted` with
 * `ProductStatus status` in the Product model (Step 2).
 * An enum is more extensible (see ProductStatus.java).
 * The query is just as fast — status is indexed.
 *
 * ──────────────────────────────────────────────────────────
 * WHY NO @Query annotations yet?
 * ──────────────────────────────────────────────────────────
 * Simple queries (find by field) → derived methods (below).
 * Complex queries (pagination + filters + search) → MongoTemplate
 * with dynamic Criteria building in ProductService (Step 6).
 *
 * @Query is for medium complexity — static MongoDB query strings.
 * We skip it and go straight to MongoTemplate for filtering.
 *
 * ──────────────────────────────────────────────────────────
 * FUTURE (v2):
 * ──────────────────────────────────────────────────────────
 * findByCategoryIdAndStatus() — already designed (compound index exists).
 * Full-text search via @TextQuery or MongoTemplate $text — Step 6.
 */
public interface ProductRepository extends MongoRepository<Product, String> {

    /**
     * Find all products with a given status.
     * Used by: public listing (ACTIVE only), admin listing (ACTIVE + INACTIVE).
     *
     * → db.products.find({ status: "ACTIVE" })
     */
    List<Product> findByStatus(ProductStatus status);

    /**
     * Find a single product by slug AND status.
     * Used by: GET /api/v1/products/{slug} (public — only ACTIVE).
     * Why include status? A deactivated product at /api/v1/products/blue-pen
     * should return 404, not the deactivated product.
     *
     * → db.products.find({ slug: "blue-pen", status: "ACTIVE" })
     */
    Optional<Product> findBySlugAndStatus(String slug, ProductStatus status);

    /**
     * Find by slug only (any status).
     * Used by: slug uniqueness check during product create/update.
     * Admin needs to know if "blue-pen" slug exists regardless of status.
     *
     * → db.products.find({ slug: "blue-pen" })
     */
    Optional<Product> findBySlug(String slug);

    /**
     * Check slug existence by prefix — for collision resolution.
     * Used by: slug generator to find "blue-pen", "blue-pen-2", etc.
     *
     * "StartingWith" → { slug: { $regex: /^blue-pen/ } }
     * This lets us find all slug variants to determine the next suffix number.
     *
     * WHY not just check exact slug? If "blue-pen-2" and "blue-pen-3" exist
     * but "blue-pen" doesn't (was deleted), we still want "blue-pen" for the
     * new product, not "blue-pen-4". Exact slug check handles that.
     * This method is used to COUNT existing collisions.
     */
    List<Product> findBySlugStartingWith(String slugPrefix);

    /**
     * Find all products in a category (any status).
     * Used by: admin — before deactivating a category, show count of affected products.
     *
     * → db.products.find({ categoryId: "..." })
     */
    List<Product> findByCategoryId(String categoryId);

    /**
     * Count products in a category by status.
     * Used by: admin dashboard — "This category has 12 active products."
     * count* methods don't load documents — just the count. Fast.
     *
     * → db.products.count({ categoryId: "...", status: "ACTIVE" })
     */
    long countByCategoryIdAndStatus(String categoryId, ProductStatus status);
}
