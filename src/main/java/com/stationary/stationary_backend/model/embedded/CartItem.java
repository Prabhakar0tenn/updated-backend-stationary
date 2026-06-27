package com.stationary.stationary_backend.model.embedded;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CartItem — Embedded document inside a Cart document.
 *
 * Stored directly inside the cart's "items" array — no separate collection.
 * WHY embed? Cart items are always accessed with the cart; never queried independently.
 * Embedding gives a single-document read for the full cart → fast.
 *
 * Snapshot fields (productName, productSlug, price) are denormalized from Product.
 * WHY? Cart items persist across sessions; if a product's price or name changes,
 * the cart should still show the price/name at the time the item was added.
 * (Frontend may choose to re-validate price at checkout — that's a separate concern.)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItem {

    /**
     * Reference to the Product document.
     * Used to: re-validate stock, fetch latest price at checkout, deep-link to product.
     */
    private String productId;

    /**
     * Snapshot of the product slug at add-time (for frontend URL building).
     */
    private String productSlug;

    /**
     * Snapshot of product name at add-time (display in cart UI).
     */
    private String productName;

    /**
     * Snapshot of product image URL at add-time (cart item thumbnail).
     */
    private String productImageUrl;

    /**
     * Snapshot of the selling price at the time the item was added.
     * Preserves price even if admin changes it later.
     */
    private Double price;

    /**
     * Snapshot of originalPrice at add-time (for discount display in cart).
     * Null if no discount existed when item was added.
     */
    private Double originalPrice;

    /**
     * How many units of this product are in the cart.
     * Always >= 1. Enforced via DTO validation.
     */
    private Integer quantity;
}
