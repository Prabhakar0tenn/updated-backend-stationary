package com.stationary.stationary_backend.mapper;

import com.stationary.stationary_backend.dto.request.CategoryRequest;
import com.stationary.stationary_backend.dto.response.CategoryResponse;
import com.stationary.stationary_backend.model.Category;
import org.mapstruct.*;

/**
 * CategoryMapper — Entity ↔ DTO mapping for Category.
 *
 * ──────────────────────────────────────────────────────────
 * Simpler than ProductMapper — no nested objects to resolve.
 * Category has no embedded documents (no equivalent to ImageData).
 * All fields map by name directly.
 * ──────────────────────────────────────────────────────────
 *
 * ──────────────────────────────────────────────────────────
 * toEntity() — ignored fields:
 * ──────────────────────────────────────────────────────────
 * slug     → generated from name in CategoryService
 * isActive → defaults to true in CategoryService
 * createdAt/updatedAt → set by @CreatedDate/@LastModifiedDate
 *
 * ──────────────────────────────────────────────────────────
 * updateCategoryFromDto():
 * ──────────────────────────────────────────────────────────
 * When admin updates a category (PUT /api/v1/categories/{id}):
 *   - name changed → slug should also be regenerated
 *   - But slug regeneration is a business decision (service layer).
 *   - The mapper just updates the fields it knows about.
 *   - Service handles slug update if name changed.
 *
 * FRESHER NOTE:
 * You'll be tempted to put slug regeneration inside the mapper
 * using @BeforeMapping or @AfterMapping. Don't.
 * Business logic in mappers = hard to test, hard to read.
 * Keep mappers pure data transformation. Business logic = service.
 */
@Mapper(componentModel = "spring")
public interface CategoryMapper {

    /**
     * CategoryRequest → new Category entity.
     * Service sets: slug, isActive=true, timestamps.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "slug", ignore = true)
    @Mapping(target = "isActive", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Category toEntity(CategoryRequest dto);

    /**
     * Category entity → CategoryResponse DTO.
     * All fields map by name. No ignored fields here.
     */
    CategoryResponse toResponse(Category entity);

    /**
     * Partial update of existing Category from DTO.
     * Slug update (if name changed) handled in service after this call.
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "slug", ignore = true)
    @Mapping(target = "isActive", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateCategoryFromDto(CategoryRequest dto, @MappingTarget Category entity);
}
