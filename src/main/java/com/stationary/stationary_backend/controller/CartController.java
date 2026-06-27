package com.stationary.stationary_backend.controller;

import com.stationary.stationary_backend.dto.request.AddToCartRequest;
import com.stationary.stationary_backend.dto.request.UpdateCartItemRequest;
import com.stationary.stationary_backend.dto.response.ApiResponse;
import com.stationary.stationary_backend.dto.response.CartResponse;
import com.stationary.stationary_backend.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * CartController — REST endpoints for the persistent cart system.
 *
 * ──────────────────────────────────────────────────────────
 * ALL endpoints are PUBLIC (no JWT required).
 * ──────────────────────────────────────────────────────────
 * Cart is for end-users who are NOT logged in.
 * Identity is carried via the X-Session-Id header (client UUID).
 *
 * ──────────────────────────────────────────────────────────
 * SESSION HEADER:
 * ──────────────────────────────────────────────────────────
 * Every request must include:
 *   X-Session-Id: <uuid>
 *
 * Frontend responsibility:
 *   1. On app init: check localStorage for existing sessionId.
 *   2. If none: generate crypto.randomUUID() and store it.
 *   3. Send it as X-Session-Id on every cart API call.
 *
 * ──────────────────────────────────────────────────────────
 * ENDPOINTS:
 * ──────────────────────────────────────────────────────────
 *   GET    /api/v1/cart               — fetch current cart
 *   POST   /api/v1/cart/items         — add item (or merge quantity)
 *   PUT    /api/v1/cart/items/{productId} — update quantity
 *   DELETE /api/v1/cart/items/{productId} — remove item
 *   DELETE /api/v1/cart               — clear entire cart
 */
@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    /**
     * Get the full cart for a session.
     * Returns an empty cart (0 items) if the session has no cart yet.
     * Never returns 404 — simplifies frontend initialization logic.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<CartResponse>> getCart(
            @RequestHeader("X-Session-Id") String sessionId) {
        CartResponse response = cartService.getCart(sessionId);
        return ResponseEntity.ok(ApiResponse.of("Cart fetched successfully", response));
    }

    /**
     * Add a product to the cart.
     * If the product is already in the cart, quantity is merged (added on top).
     * Validates: product exists, product is ACTIVE, sufficient stock.
     */
    @PostMapping("/items")
    public ResponseEntity<ApiResponse<CartResponse>> addItem(
            @RequestHeader("X-Session-Id") String sessionId,
            @Valid @RequestBody AddToCartRequest request) {
        CartResponse response = cartService.addItem(sessionId, request);
        return ResponseEntity.ok(ApiResponse.of("Item added to cart", response));
    }

    /**
     * Update the quantity of a specific item in the cart.
     * Item must already exist in the cart (use POST /items to add first).
     * Quantity must be >= 1 (use DELETE /items/{productId} to remove).
     */
    @PutMapping("/items/{productId}")
    public ResponseEntity<ApiResponse<CartResponse>> updateItem(
            @RequestHeader("X-Session-Id") String sessionId,
            @PathVariable String productId,
            @Valid @RequestBody UpdateCartItemRequest request) {
        CartResponse response = cartService.updateItem(sessionId, productId, request);
        return ResponseEntity.ok(ApiResponse.of("Cart item updated", response));
    }

    /**
     * Remove a specific product from the cart.
     * Returns the updated cart state after removal.
     */
    @DeleteMapping("/items/{productId}")
    public ResponseEntity<ApiResponse<CartResponse>> removeItem(
            @RequestHeader("X-Session-Id") String sessionId,
            @PathVariable String productId) {
        CartResponse response = cartService.removeItem(sessionId, productId);
        return ResponseEntity.ok(ApiResponse.of("Item removed from cart", response));
    }

    /**
     * Clear the entire cart (all items removed).
     * Typically called after a WhatsApp order is successfully sent.
     * Returns 200 with no data body.
     */
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> clearCart(
            @RequestHeader("X-Session-Id") String sessionId) {
        cartService.clearCart(sessionId);
        return ResponseEntity.ok(ApiResponse.ok("Cart cleared"));
    }
}
