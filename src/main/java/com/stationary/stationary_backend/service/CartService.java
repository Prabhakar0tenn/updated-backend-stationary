package com.stationary.stationary_backend.service;

import com.stationary.stationary_backend.dto.request.AddToCartRequest;
import com.stationary.stationary_backend.dto.request.UpdateCartItemRequest;
import com.stationary.stationary_backend.dto.response.CartItemResponse;
import com.stationary.stationary_backend.dto.response.CartResponse;
import com.stationary.stationary_backend.exception.BadRequestException;
import com.stationary.stationary_backend.exception.ResourceNotFoundException;
import com.stationary.stationary_backend.model.Cart;
import com.stationary.stationary_backend.model.Product;
import com.stationary.stationary_backend.model.embedded.CartItem;
import com.stationary.stationary_backend.model.enums.ProductStatus;
import com.stationary.stationary_backend.repository.CartRepository;
import com.stationary.stationary_backend.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * CartService — Business logic for the persistent cart system.
 *
 * ──────────────────────────────────────────────────────────
 * SESSION-BASED IDENTITY:
 * ──────────────────────────────────────────────────────────
 * Carts are identified by a client-generated UUID (sessionId).
 * The frontend stores this in localStorage and sends it as the
 * X-Session-Id header on every cart request.
 *
 * When customer auth arrives in v2, the service can additionally
 * link carts to user accounts — existing sessionId logic stays intact.
 *
 * ──────────────────────────────────────────────────────────
 * STOCK VALIDATION:
 * ──────────────────────────────────────────────────────────
 * On addItem: checks that requested quantity <= product.stock.
 * On updateItem: same check.
 * Stock is NOT decremented here — decrement happens at order creation.
 * (Decrementing at cart-add would over-block stock for abandoned carts.)
 *
 * ──────────────────────────────────────────────────────────
 * PRICE SNAPSHOT:
 * ──────────────────────────────────────────────────────────
 * Price is captured at add-time from the product entity.
 * Subsequent product price changes don't affect existing cart items.
 * This is intentional — user expects price stability in their cart.
 *
 * ──────────────────────────────────────────────────────────
 * WHATSAPP MESSAGE:
 * ──────────────────────────────────────────────────────────
 * The buildWhatsAppMessage() method generates a human-readable,
 * emoji-formatted order summary. Frontend opens:
 *   https://wa.me/91XXXXXXXXXX?text=<url-encoded-message>
 * No business phone number is embedded here — that's a frontend concern.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CartService {

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;

    // ── GET CART ──────────────────────────────────────────────────────────

    /**
     * Fetch the cart for a session. Returns an empty cart (no DB record yet)
     * if this session has no cart, so the frontend always gets a valid response.
     */
    public CartResponse getCart(String sessionId) {
        validateSessionId(sessionId);
        Cart cart = cartRepository.findBySessionId(sessionId)
                .orElse(Cart.builder().sessionId(sessionId).items(new ArrayList<>()).build());
        return buildCartResponse(cart);
    }

    // ── ADD ITEM ──────────────────────────────────────────────────────────

    /**
     * Add a product to the cart (or increase quantity if already present).
     *
     * If the same productId already exists in the cart, quantities are MERGED
     * (not replaced). This matches natural shopping behavior.
     */
    public CartResponse addItem(String sessionId, AddToCartRequest request) {
        validateSessionId(sessionId);

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", request.getProductId()));

        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new BadRequestException("Product is not available: " + product.getName());
        }
        if (product.getStock() < request.getQuantity()) {
            throw new BadRequestException(
                    "Insufficient stock. Available: " + product.getStock());
        }

        Cart cart = cartRepository.findBySessionId(sessionId)
                .orElse(Cart.builder().sessionId(sessionId).items(new ArrayList<>()).build());

        // Merge if product already in cart
        Optional<CartItem> existing = cart.getItems().stream()
                .filter(i -> i.getProductId().equals(request.getProductId()))
                .findFirst();

        if (existing.isPresent()) {
            CartItem item = existing.get();
            int newQty = item.getQuantity() + request.getQuantity();
            if (newQty > product.getStock()) {
                throw new BadRequestException(
                        "Total quantity would exceed available stock (" + product.getStock() + ")");
            }
            item.setQuantity(newQty);
        } else {
            cart.getItems().add(CartItem.builder()
                    .productId(product.getId())
                    .productSlug(product.getSlug())
                    .productName(product.getName())
                    .productImageUrl(product.getImage() != null ? product.getImage().getUrl() : null)
                    .price(product.getPrice())
                    .originalPrice(product.getOriginalPrice())
                    .quantity(request.getQuantity())
                    .build());
        }

        Cart saved = cartRepository.save(cart);
        log.debug("Cart [{}]: added product '{}' ×{}", sessionId, product.getName(), request.getQuantity());
        return buildCartResponse(saved);
    }

    // ── UPDATE ITEM QUANTITY ───────────────────────────────────────────────

    /**
     * Update the quantity of a specific item in the cart.
     * Throws 404 if the item is not found in the cart.
     */
    public CartResponse updateItem(String sessionId, String productId, UpdateCartItemRequest request) {
        validateSessionId(sessionId);

        Cart cart = cartRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart", "sessionId", sessionId));

        CartItem item = cart.getItems().stream()
                .filter(i -> i.getProductId().equals(productId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("CartItem", "productId", productId));

        // Re-check stock against current product data
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", productId));

        if (request.getQuantity() > product.getStock()) {
            throw new BadRequestException(
                    "Requested quantity exceeds available stock (" + product.getStock() + ")");
        }

        item.setQuantity(request.getQuantity());
        Cart saved = cartRepository.save(cart);
        log.debug("Cart [{}]: updated product '{}' to qty {}", sessionId, productId, request.getQuantity());
        return buildCartResponse(saved);
    }

    // ── REMOVE ITEM ───────────────────────────────────────────────────────

    /**
     * Remove a specific product from the cart.
     */
    public CartResponse removeItem(String sessionId, String productId) {
        validateSessionId(sessionId);

        Cart cart = cartRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart", "sessionId", sessionId));

        boolean removed = cart.getItems().removeIf(i -> i.getProductId().equals(productId));
        if (!removed) {
            throw new ResourceNotFoundException("CartItem", "productId", productId);
        }

        Cart saved = cartRepository.save(cart);
        log.debug("Cart [{}]: removed product '{}'", sessionId, productId);
        return buildCartResponse(saved);
    }

    // ── CLEAR CART ────────────────────────────────────────────────────────

    /**
     * Remove all items from the cart (used after WhatsApp order is sent).
     * Deletes the cart document entirely for clean storage.
     */
    public void clearCart(String sessionId) {
        validateSessionId(sessionId);
        cartRepository.deleteBySessionId(sessionId);
        log.debug("Cart [{}]: cleared", sessionId);
    }

    // ── INTERNAL HELPERS ──────────────────────────────────────────────────

    /**
     * Map a Cart entity to CartResponse, computing all aggregated values.
     */
    private CartResponse buildCartResponse(Cart cart) {
        List<CartItemResponse> itemResponses = cart.getItems().stream()
                .map(this::toItemResponse)
                .collect(Collectors.toList());

        int totalItems = itemResponses.stream()
                .mapToInt(CartItemResponse::getQuantity)
                .sum();

        double grandTotal = itemResponses.stream()
                .mapToDouble(CartItemResponse::getLineTotal)
                .sum();
        // Round to 2 decimal places
        grandTotal = Math.round(grandTotal * 100.0) / 100.0;

        String whatsappMessage = buildWhatsAppMessage(itemResponses, grandTotal);

        return CartResponse.builder()
                .sessionId(cart.getSessionId())
                .items(itemResponses)
                .totalItems(totalItems)
                .grandTotal(grandTotal)
                .whatsappMessage(whatsappMessage)
                .lastModifiedAt(cart.getLastModifiedAt())
                .build();
    }

    /**
     * Map a CartItem embedded document to CartItemResponse.
     */
    private CartItemResponse toItemResponse(CartItem item) {
        double lineTotal = Math.round(item.getPrice() * item.getQuantity() * 100.0) / 100.0;
        Double discountAmount = null;
        if (item.getOriginalPrice() != null && item.getOriginalPrice() > item.getPrice()) {
            discountAmount = Math.round((item.getOriginalPrice() - item.getPrice()) * 100.0) / 100.0;
        }
        return CartItemResponse.builder()
                .productId(item.getProductId())
                .productSlug(item.getProductSlug())
                .productName(item.getProductName())
                .productImageUrl(item.getProductImageUrl())
                .price(item.getPrice())
                .originalPrice(item.getOriginalPrice())
                .discountAmount(discountAmount)
                .quantity(item.getQuantity())
                .lineTotal(lineTotal)
                .build();
    }

    /**
     * Build a human-readable WhatsApp order message combining all cart items.
     *
     * Example output (before URL-encoding):
     *   🛒 *New Order*
     *
     *   1. Blue Gel Pen × 2 = ₹120
     *   2. A4 Notebook × 1 = ₹60
     *   ──────────────────
     *   💰 *Total: ₹180*
     *
     *   Please confirm availability and share payment details. Thank you! 🙏
     *
     * The returned string is plain text (NOT URL-encoded).
     * URL-encoding is done on the frontend when building the wa.me link.
     */
    private String buildWhatsAppMessage(List<CartItemResponse> items, double grandTotal) {
        if (items.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("🛒 *New Order*\n\n");

        for (int i = 0; i < items.size(); i++) {
            CartItemResponse item = items.get(i);
            sb.append(i + 1).append(". ")
              .append(item.getProductName())
              .append(" × ").append(item.getQuantity())
              .append(" = ₹").append(String.format("%.0f", item.getLineTotal()));

            // Append discount info if applicable
            if (item.getOriginalPrice() != null && item.getDiscountAmount() != null) {
                sb.append(" ~~₹").append(String.format("%.0f", item.getOriginalPrice() * item.getQuantity())).append("~~");
            }
            sb.append("\n");
        }

        sb.append("──────────────────\n");
        sb.append("💰 *Total: ₹").append(String.format("%.0f", grandTotal)).append("*\n\n");
        sb.append("Please confirm availability and share payment details. Thank you! 🙏");

        return sb.toString();
    }

    /**
     * Validates that sessionId is not blank (should never be blank due to controller checks,
     * but an extra guard prevents accidental null cart merges).
     */
    private void validateSessionId(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            throw new BadRequestException("X-Session-Id header is required");
        }
    }
}
