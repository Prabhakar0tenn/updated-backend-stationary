package com.stationary.stationary_backend.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * AddToCartRequest — Body for POST /api/v1/cart/items
 *
 * The client sends the productId and desired quantity.
 * SessionId is passed as a header (X-Session-Id), not in this body,
 * so the same DTO can be reused if auth is added in v2.
 */
@Data
public class AddToCartRequest {

    @NotBlank(message = "productId is required")
    private String productId;

    @NotNull(message = "quantity is required")
    @Min(value = 1, message = "quantity must be at least 1")
    private Integer quantity;
}
