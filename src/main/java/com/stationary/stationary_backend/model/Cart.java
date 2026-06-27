package com.stationary.stationary_backend.model;

import com.stationary.stationary_backend.model.embedded.CartItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Cart — MongoDB document mapping the "carts" collection.
 *
 * Design decisions:
 *
 * ONE CART PER SESSION ID:
 *   The app currently has no customer auth (admin-only).
 *   Cart is identified by a session ID (UUID) provided by the frontend.
 *   When customer auth is added in v2, sessionId can be replaced/supplemented
 *   with customerId — no structural change needed.
 *
 * WHY sessionId and not IP / device fingerprint?
 *   IPs can be shared (NAT). Fingerprints are complex.
 *   UUID session token generated on first cart interaction is simple,
 *   stateless-friendly, and hard to guess.
 *
 * CART PERSISTENCE:
 *   Cart lives in MongoDB (not Redis / in-memory).
 *   Survives page refresh, browser close, device switch (same sessionId).
 *   TTL index on lastModifiedAt (30 days) auto-expires abandoned carts.
 *   (TTL index must be created manually in MongoDB or via a migration script.)
 *
 * ITEMS EMBEDDED (not a separate collection):
 *   Cart items are always read/written WITH the cart.
 *   Embedding them avoids a JOIN and keeps the cart atomic.
 *   MongoDB document size limit (16 MB) is not a concern for cart sizes.
 */
@Document("carts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Cart {

    @Id
    private String id;

    /**
     * Client-generated UUID identifying this cart.
     * Frontend must persist this (localStorage) and send in every cart request.
     * Indexed with unique=true to enforce one-cart-per-session.
     */
    @Indexed(unique = true)
    private String sessionId;

    /**
     * The list of items currently in this cart.
     * Initialized to empty list to avoid NPE on first add.
     */
    @Builder.Default
    private List<CartItem> items = new ArrayList<>();

    /**
     * Auto-updated on every save (requires @EnableMongoAuditing).
     * Used for TTL expiry and "cart last updated" display.
     */
    @LastModifiedDate
    private Instant lastModifiedAt;
}
