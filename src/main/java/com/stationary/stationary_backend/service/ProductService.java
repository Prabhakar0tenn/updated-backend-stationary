package com.stationary.stationary_backend.service;

import com.stationary.stationary_backend.dto.request.ProductRequest;
import com.stationary.stationary_backend.dto.response.CategoryResponse;
import com.stationary.stationary_backend.dto.response.ProductResponse;
import com.stationary.stationary_backend.dto.response.ProductSummaryResponse;
import com.stationary.stationary_backend.exception.BadRequestException;
import com.stationary.stationary_backend.exception.ResourceNotFoundException;
import com.stationary.stationary_backend.mapper.ProductMapper;
import com.stationary.stationary_backend.model.Category;
import com.stationary.stationary_backend.model.Product;
import com.stationary.stationary_backend.model.enums.ProductStatus;
import com.stationary.stationary_backend.repository.CategoryRepository;
import com.stationary.stationary_backend.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * ProductService — Full implementation with pagination, filtering,
 * text search, slug generation, and Redis caching.
 *
 * ──────────────────────────────────────────────────────────
 * WHY MongoTemplate instead of Repository for the listing query?
 * ──────────────────────────────────────────────────────────
 * ProductRepository works for simple derived queries (findByStatus, findBySlug).
 * But the public listing has DYNAMIC criteria:
 *   - category filter (optional)
 *   - price range (optional min + max)
 *   - text search (optional)
 *   - stock filter (optional)
 *   - pagination + sorting
 *
 * A derived method like findByStatusAndCategoryIdAndPriceBetween() only handles
 * a FIXED set of filters. If any filter is optional, the method doesn't work —
 * you'd need 2^N methods for all filter combinations.
 *
 * MongoTemplate + Criteria builder = programmatically add filters only when present.
 * This is the correct tool for dynamic, optional multi-field queries.
 *
 * ──────────────────────────────────────────────────────────
 * SLUG GENERATION + COLLISION RESOLUTION:
 * ──────────────────────────────────────────────────────────
 * "Blue Gel Pen" → "blue-gel-pen"
 * If "blue-gel-pen" exists: try "blue-gel-pen-2", "blue-gel-pen-3", etc.
 * Uses findBySlugStartingWith() to find all existing variants in one query.
 *
 * ──────────────────────────────────────────────────────────
 * CACHING DESIGN:
 * ──────────────────────────────────────────────────────────
 * - Single product by slug: cached 30 min ("product" cache)
 * - Paginated list: NOT cached with @Cacheable (too many filter combos)
 *   TODO: Add Redis manual caching for the top-N most common queries in v2
 * - Categories: fetched once per service call via @Cacheable("categories")
 *
 * ──────────────────────────────────────────────────────────
 * N+1 PROBLEM PREVENTION:
 * ──────────────────────────────────────────────────────────
 * Each ProductSummaryResponse needs a categoryName.
 * Each Product has only a categoryId (String).
 *
 * WRONG approach (N+1):
 *   for (Product p : products) {
 *     Category c = categoryRepository.findById(p.getCategoryId());  // N DB calls!
 *     summary.setCategoryName(c.getName());
 *   }
 *
 * RIGHT approach (1 extra query):
 *   1. Collect all unique categoryIds from the page
 *   2. Fetch all needed categories in ONE query (findAllById)
 *   3. Build a Map<String categoryId, Category>
 *   4. Lookup from the map (O(1)) while building each summary
 *
 * Page of 24 products: 1 products query + 1 categories query = 2 total.
 * Not 1 + 24 = 25.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;
    private final MongoTemplate mongoTemplate;
    private final CategoryService categoryService;  // reuse slug util + cache

    // ── Public READ ───────────────────────────────────────────────────────

    /**
     * Paginated product listing with optional filters.
     *
     * @param page       page number (0-indexed)
     * @param size       items per page (default 12, max 48)
     * @param sort       field to sort by: "price", "createdAt", "name"
     * @param direction  "asc" or "desc"
     * @param category   category slug filter (optional)
     * @param search     full-text search query (optional)
     * @param minPrice   minimum price filter (optional)
     * @param maxPrice   maximum price filter (optional)
     * @param inStock    if true, only show products with stock > 0
     * @return Page of ProductSummaryResponse
     */
    public Page<ProductSummaryResponse> getProducts(
            int page, int size, String sort, String direction,
            String category, String search,
            Double minPrice, Double maxPrice,
            Boolean inStock
    ) {
        // ── Sanitize pagination params ─────────────────────────────────────
        size = Math.min(size, 48);      // max 48 per page — prevent abuse
        page = Math.max(page, 0);       // negative page → page 0

        // ── Resolve category slug → categoryId ─────────────────────────────
        String categoryId = null;
        if (StringUtils.hasText(category)) {
            categoryId = categoryRepository.findBySlugAndIsActiveTrue(category)
                    .map(Category::getId)
                    .orElse(null);  // invalid slug → treat as no filter (returns empty naturally)
        }

        // ── Build sort ─────────────────────────────────────────────────────
        Sort.Direction sortDirection = "asc".equalsIgnoreCase(direction)
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        String sortField = resolveSortField(sort);
        Sort sortObj = Sort.by(sortDirection, sortField);
        Pageable pageable = PageRequest.of(page, size, sortObj);

        // ── Build MongoDB Criteria dynamically ────────────────────────────
        // We ALWAYS filter by status = ACTIVE (public endpoint)
        Query query = new Query();
        query.addCriteria(Criteria.where("status").is(ProductStatus.ACTIVE));

        // Optional category filter
        if (categoryId != null) {
            query.addCriteria(Criteria.where("categoryId").is(categoryId));
        }

        // Optional price range
        if (minPrice != null && maxPrice != null) {
            query.addCriteria(Criteria.where("price").gte(minPrice).lte(maxPrice));
        } else if (minPrice != null) {
            query.addCriteria(Criteria.where("price").gte(minPrice));
        } else if (maxPrice != null) {
            query.addCriteria(Criteria.where("price").lte(maxPrice));
        }

        // Optional in-stock filter
        if (Boolean.TRUE.equals(inStock)) {
            query.addCriteria(Criteria.where("stock").gt(0));
        }

        // Optional search query (using safe regex matching on name, description, or tags)
        if (StringUtils.hasText(search)) {
            String escapedSearch = java.util.regex.Pattern.quote(search);
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("name").regex(escapedSearch, "i"),
                    Criteria.where("description").regex(escapedSearch, "i"),
                    Criteria.where("tags").regex(escapedSearch, "i")
            ));
        }

        // ── Execute: count + paginated fetch (2 queries total) ────────────
        long total = mongoTemplate.count(query, Product.class);

        query.with(pageable);
        List<Product> products = mongoTemplate.find(query, Product.class);

        // ── Resolve category names (N+1 prevention) ──────────────────────
        List<ProductSummaryResponse> summaries = buildSummariesWithCategories(products);

        return new PageImpl<>(summaries, pageable, total);
    }

    /**
     * Single product detail by slug (PUBLIC — ACTIVE only).
     * Cached 30 min in Redis.
     *
     * WHY cache by slug? The product detail page is the MOST visited page
     * (every product click). Caching it reduces DB load significantly.
     */
    @Cacheable(value = "product", key = "#slug")
    public ProductResponse getBySlug(String slug) {
        log.debug("Cache miss — fetching product by slug: {}", slug);
        Product product = productRepository.findBySlugAndStatus(slug, ProductStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "slug", slug));
        return buildProductResponse(product);
    }

    // ── Admin READ ─────────────────────────────────────────────────────────

    /**
     * All products (ACTIVE + INACTIVE) for admin panel.
     * No cache — admin needs real-time data.
     */
    public Page<ProductSummaryResponse> getProductsForAdmin(int page, int size) {
        size = Math.min(size, 48);
        Pageable pageable = PageRequest.of(Math.max(page, 0), size,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Query query = new Query().with(pageable);
        long total = mongoTemplate.count(new Query(), Product.class);
        List<Product> products = mongoTemplate.find(query, Product.class);

        return new PageImpl<>(buildSummariesWithCategories(products), pageable, total);
    }

    // ── Admin WRITE ───────────────────────────────────────────────────────

    /**
     * Create a new product.
     *
     * @CacheEvict: clears "product" cache entirely.
     * Why not evict specific key? We don't know the new slug yet at annotation time.
     * allEntries=true clears all cached products (safe, TTL is 30 min anyway).
     */
    @CacheEvict(value = "product", allEntries = true)
    public ProductResponse create(ProductRequest request) {
        // Validate category exists
        categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", request.getCategoryId()));

        // Validate originalPrice > price when provided
        validateDiscount(request);

        Product product = productMapper.toEntity(request);
        product.setSlug(generateUniqueSlug(request.getName()));
        product.setStatus(ProductStatus.ACTIVE);

        Product saved = productRepository.save(product);
        log.info("Product created: '{}' (slug: {})", saved.getName(), saved.getSlug());
        return buildProductResponse(saved);
    }

    /**
     * Full update of a product (PUT semantics — all fields replaced).
     *
     * Image change detection: if admin sends a different image publicId,
     * we delete the old image from Cloudinary.
     *
     * @Caching: evict both the specific cached slug AND the admin list
     */
    @Caching(evict = {
            @CacheEvict(value = "product", key = "#id"),
            @CacheEvict(value = "product", allEntries = true)
    })
    public ProductResponse update(String id, ProductRequest request, ImageService imageService) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));

        // Validate the new category exists (if changed)
        categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", request.getCategoryId()));

        // Validate originalPrice > price when provided
        validateDiscount(request);

        // Image cleanup: delete old Cloudinary image if a new one is provided
        boolean imageChanged = product.getImage() != null
                && !Objects.equals(product.getImage().getPublicId(), request.getImage().getPublicId());
        String oldPublicId = imageChanged ? product.getImage().getPublicId() : null;

        // Slug update: if name changed, regenerate slug
        boolean nameChanged = !product.getName().equals(request.getName());
        String newSlug = nameChanged ? generateUniqueSlugExcluding(request.getName(), id) : product.getSlug();

        productMapper.updateProductFromDto(request, product);
        product.setSlug(newSlug);

        Product saved = productRepository.save(product);

        // Delete old image AFTER successful save (don't block save on Cloudinary failure)
        if (oldPublicId != null) {
            imageService.delete(oldPublicId);
        }

        log.info("Product updated: '{}'", saved.getName());
        return buildProductResponse(saved);
    }

    /**
     * Toggle product status (ACTIVE ↔ INACTIVE).
     * Used for quick enable/disable from admin panel.
     */
    @CacheEvict(value = "product", allEntries = true)
    public ProductResponse toggleStatus(String id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));

        product.setStatus(product.getStatus() == ProductStatus.ACTIVE
                ? ProductStatus.INACTIVE : ProductStatus.ACTIVE);

        Product saved = productRepository.save(product);
        log.info("Product '{}' status toggled to: {}", saved.getName(), saved.getStatus());
        return buildProductResponse(saved);
    }

    /**
     * Soft delete a product (sets status to INACTIVE).
     *
     * WHY soft delete (not hard delete)?
     * 1. Preserves the slug — prevents someone else from creating a product
     *    with the same slug and potentially confusing search engines.
     * 2. Audit trail — admin can see what was deleted and when.
     * 3. Accidental deletes are recoverable.
     *
     * Cleanup: deletes associated Cloudinary image (permanent action).
     */
    @CacheEvict(value = "product", allEntries = true)
    public void delete(String id, ImageService imageService) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));

        // Clean up Cloudinary image before marking deleted
        if (product.getImage() != null && product.getImage().getPublicId() != null) {
            imageService.delete(product.getImage().getPublicId());
        }

        product.setStatus(ProductStatus.INACTIVE);
        productRepository.save(product);
        log.info("Product '{}' soft-deleted", product.getName());
    }

    // ── Internal helpers ──────────────────────────────────────────────────

    /**
     * Build ProductResponse with category populated.
     * Fetches category by ID to fill in the category nested object.
     */
    private ProductResponse buildProductResponse(Product product) {
        ProductResponse response = productMapper.toResponse(product);

        // Populate category: fetch by ID, map to CategoryResponse
        if (product.getCategoryId() != null) {
            categoryRepository.findById(product.getCategoryId())
                    .map(productMapper::categoryToResponse)
                    .ifPresent(response::setCategory);
        }

        // Populate computed discount fields
        applyDiscountToResponse(product, response);

        return response;
    }

    /**
     * Build list of ProductSummaryResponse with category names.
     * Uses batch category fetch to avoid N+1 queries.
     *
     * MENTOR NOTE — This is a critical pattern every backend dev must know.
     * The naive approach (one DB call per product) causes the N+1 problem.
     * We collect all needed IDs first, fetch them in ONE call, then map.
     */
    private List<ProductSummaryResponse> buildSummariesWithCategories(List<Product> products) {
        // 1. Collect unique category IDs from this page of products
        List<String> categoryIds = products.stream()
                .map(Product::getCategoryId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        // 2. Fetch all needed categories in ONE query
        Map<String, Category> categoryMap = categoryRepository.findAllById(categoryIds)
                .stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));

        // 3. Build summaries — O(1) category name lookup from map
        return products.stream()
                .map(product -> {
                    ProductSummaryResponse summary = productMapper.toSummary(product);
                    Category cat = categoryMap.get(product.getCategoryId());
                    if (cat != null) {
                        summary.setCategoryName(cat.getName());
                    }
                    // Populate computed discount fields
                    applyDiscountToSummary(product, summary);
                    return summary;
                })
                .collect(Collectors.toList());
    }

    /**
     * Validates that originalPrice > price when originalPrice is provided.
     * Keeps this logic in the service so the DTO stays simple.
     */
    private void validateDiscount(ProductRequest request) {
        if (request.getOriginalPrice() != null && request.getPrice() != null) {
            if (request.getOriginalPrice() <= request.getPrice()) {
                throw new BadRequestException(
                        "originalPrice must be greater than price");
            }
        }
    }

    /**
     * Computes discountAmount and discountPercentage from the entity
     * and sets them on the full ProductResponse.
     * Safe when originalPrice is null — fields remain null (omitted by @JsonInclude).
     */
    private void applyDiscountToResponse(Product product, ProductResponse response) {
        if (product.getOriginalPrice() != null && product.getPrice() != null
                && product.getOriginalPrice() > product.getPrice()) {
            double amount = product.getOriginalPrice() - product.getPrice();
            double pct = Math.round((amount / product.getOriginalPrice()) * 1000.0) / 10.0;
            response.setDiscountAmount(amount);
            response.setDiscountPercentage(pct);
        }
    }

    /**
     * Same computation for ProductSummaryResponse (product cards).
     */
    private void applyDiscountToSummary(Product product, ProductSummaryResponse summary) {
        if (product.getOriginalPrice() != null && product.getPrice() != null
                && product.getOriginalPrice() > product.getPrice()) {
            double amount = product.getOriginalPrice() - product.getPrice();
            double pct = Math.round((amount / product.getOriginalPrice()) * 1000.0) / 10.0;
            summary.setDiscountAmount(amount);
            summary.setDiscountPercentage(pct);
        }
    }

    /**
     * Generates a unique slug for a new product.
     * If "blue-gel-pen" exists: tries "blue-gel-pen-2", "blue-gel-pen-3", ...
     *
     * WHY find by prefix? findBySlugStartingWith("blue-gel-pen") returns
     * ["blue-gel-pen", "blue-gel-pen-2", "blue-gel-pen-3"] in ONE query.
     * We then find the next available number.
     */
    private String generateUniqueSlug(String name) {
        String base = slugify(name);
        List<Product> existing = productRepository.findBySlugStartingWith(base);

        if (existing.isEmpty()) return base;

        // Find the highest suffix number currently used
        int maxSuffix = 1;
        for (Product p : existing) {
            if (p.getSlug().equals(base)) {
                maxSuffix = Math.max(maxSuffix, 1);
            } else if (p.getSlug().startsWith(base + "-")) {
                try {
                    int suffix = Integer.parseInt(p.getSlug().substring(base.length() + 1));
                    maxSuffix = Math.max(maxSuffix, suffix);
                } catch (NumberFormatException ignored) {
                    // slug has a non-numeric suffix — skip
                }
            }
        }
        return base + "-" + (maxSuffix + 1);
    }

    /**
     * Generates a unique slug for an UPDATE, excluding the current product's slug.
     * Without exclusion, updating a product would detect its OWN slug as a collision.
     */
    private String generateUniqueSlugExcluding(String name, String productId) {
        String base = slugify(name);
        List<Product> existing = productRepository.findBySlugStartingWith(base)
                .stream()
                .filter(p -> !p.getId().equals(productId))  // exclude self
                .collect(Collectors.toList());

        if (existing.isEmpty()) return base;

        int maxSuffix = 1;
        for (Product p : existing) {
            if (p.getSlug().equals(base)) {
                maxSuffix = Math.max(maxSuffix, 1);
            } else if (p.getSlug().startsWith(base + "-")) {
                try {
                    int suffix = Integer.parseInt(p.getSlug().substring(base.length() + 1));
                    maxSuffix = Math.max(maxSuffix, suffix);
                } catch (NumberFormatException ignored) {}
            }
        }
        return base + "-" + (maxSuffix + 1);
    }

    /**
     * Validates and normalizes the sort field name.
     * WHY whitelist? Never pass user input directly as a MongoDB field name —
     * could expose internal fields. Only allow explicitly listed sort fields.
     */
    private String resolveSortField(String sort) {
        return switch (sort == null ? "" : sort.toLowerCase()) {
            case "price"     -> "price";
            case "name"      -> "name";
            case "stock"     -> "stock";
            default          -> "createdAt";  // default: newest first
        };
    }

    /**
     * Converts name to URL-safe slug string.
     * "Blue Gel Pen!" → "blue-gel-pen"
     */
    private String slugify(String name) {
        return Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("[^\\p{ASCII}]", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s-]", "")
                .trim()
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-");
    }
}
