package com.stationary.stationary_backend.mapper;

import com.stationary.stationary_backend.dto.request.ProductRequest;
import com.stationary.stationary_backend.dto.response.CategoryResponse;
import com.stationary.stationary_backend.dto.response.ProductResponse;
import com.stationary.stationary_backend.dto.response.ProductSummaryResponse;
import com.stationary.stationary_backend.dto.shared.ImageDto;
import com.stationary.stationary_backend.model.Category;
import com.stationary.stationary_backend.model.Product;
import com.stationary.stationary_backend.model.embedded.ImageData;
import org.mapstruct.*;

/**
 * ProductMapper — Full version with all mappings.
 *
 * ──────────────────────────────────────────────────────────
 * ImageData ↔ ImageDto mapping:
 * ──────────────────────────────────────────────────────────
 * ImageData (entity) and ImageDto (DTO) have the same field names.
 * MapStruct maps them automatically by name match.
 * We just need to declare the method — no @Mapping annotations needed.
 *
 * ──────────────────────────────────────────────────────────
 * toSummary() — categoryName is NOT in Product entity:
 * ──────────────────────────────────────────────────────────
 * ProductSummaryResponse.categoryName comes from Category.name,
 * but Product only has categoryId (not the Category object).
 *
 * WHY @Mapping(target = "categoryName", ignore = true) here?
 * MapStruct can't auto-resolve a join (categoryId → Category.name).
 * We handle this in ProductService: after mapping, we set
 *   summary.setCategoryName(category.getName())
 * The mapper handles the simple field mappings; service handles joins.
 *
 * ──────────────────────────────────────────────────────────
 * toResponse() — same issue with category field:
 * ──────────────────────────────────────────────────────────
 * ProductResponse.category is a CategoryResponse object.
 * Product only has categoryId (String).
 * Again: mapper ignores it, service sets it after fetching Category.
 *
 * This is the correct separation:
 *   Mapper: field-to-field transformation (pure data mapping)
 *   Service: business logic (fetching related data, applying rules)
 */
@Mapper(componentModel = "spring")
public interface ProductMapper {

    // ── Entity ↔ Embedded mapping ─────────────────────────────────────────

    /**
     * MapStruct auto-maps publicId → publicId, url → url.
     * No @Mapping needed — same field names.
     */
    ImageDto toImageDto(ImageData imageData);

    ImageData toImageData(ImageDto imageDto);

    // ── Request → Entity ──────────────────────────────────────────────────

    /**
     * Maps ProductRequest → new Product entity.
     * Used in: ProductService.createProduct()
     *
     * Ignored fields are set by service layer:
     *   slug      → generated from name
     *   status    → defaults to ACTIVE
     *   createdAt → set by @CreatedDate (MongoAuditing)
     *   updatedAt → set by @LastModifiedDate (MongoAuditing)
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "slug", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Product toEntity(ProductRequest dto);

    /**
     * Partial update of existing Product from DTO.
     * Used in: ProductService.updateProduct()
     *
     * NullValuePropertyMappingStrategy.IGNORE:
     * Fields not present in dto (null) keep their entity values.
     * Enables PATCH semantics — send only changed fields.
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "slug", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "status", ignore = true)
    void updateProductFromDto(ProductRequest dto, @MappingTarget Product entity);

    // ── Entity → Response ─────────────────────────────────────────────────

    /**
     * Maps Product → full ProductResponse.
     * Used in: GET /api/v1/products/{slug}
     *
     * category field: ignored here, set in service after fetching Category.
     * image: MapStruct calls toImageDto(product.getImage()) automatically
     *        because it sees ImageData → ImageDto method above.
     */
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "discountAmount", ignore = true)
    @Mapping(target = "discountPercentage", ignore = true)
    ProductResponse toResponse(Product entity);

    /**
     * Maps Product → lightweight ProductSummaryResponse.
     * Used in: GET /api/v1/products (list page)
     *
     * categoryName: ignored here, set in service from Category.name.
     */
    @Mapping(target = "categoryName", ignore = true)
    @Mapping(target = "discountAmount", ignore = true)
    @Mapping(target = "discountPercentage", ignore = true)
    ProductSummaryResponse toSummary(Product entity);

    /**
     * Maps Category entity → CategoryResponse.
     * Used by ProductService to populate the nested category field
     * in ProductResponse WITHOUT importing CategoryMapper.
     * MapStruct generates this automatically — field names match.
     */
    CategoryResponse categoryToResponse(Category category);
}
