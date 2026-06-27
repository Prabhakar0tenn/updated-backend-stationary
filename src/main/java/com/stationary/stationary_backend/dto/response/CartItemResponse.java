package com.stationary.stationary_backend.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

/**
 * CartItemResponse — One line item in the cart response.
 * Returned as part of CartResponse.items list.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CartItemResponse {

    private String productId;
    private String productSlug;
    private String productName;
    private String productImageUrl;

    /** Selling price (snapshot at add-time). */
    private Double price;

    /** MRP price (snapshot at add-time). Null if no discount existed. */
    private Double originalPrice;

    /** Discount amount = originalPrice - price. Null if no discount. */
    private Double discountAmount;

    private Integer quantity;

    /** Line total = price × quantity. */
    private Double lineTotal;
}
