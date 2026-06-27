package com.stationary.stationary_backend.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * UpdateCartItemRequest — Body for PUT /api/v1/cart/items/{productId}
 *
 * Only quantity can be updated. Setting quantity to 0 is not allowed
 * (use DELETE /api/v1/cart/items/{productId} to remove an item instead).
 */
@Data
public class UpdateCartItemRequest {

    @NotNull(message = "quantity is required")
    @Min(value = 1, message = "quantity must be at least 1")
    private Integer quantity;
}
