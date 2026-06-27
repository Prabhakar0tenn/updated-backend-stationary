package com.stationary.stationary_backend.repository;

import com.stationary.stationary_backend.model.Cart;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

/**
 * CartRepository — Data access for the "carts" collection.
 *
 * The primary lookup key is sessionId (not _id).
 * Clients never know the internal _id — they only hold their sessionId UUID.
 */
public interface CartRepository extends MongoRepository<Cart, String> {

    /**
     * Find the cart for a given session.
     * Returns empty Optional if this is a new session (no cart yet).
     *
     * → db.carts.findOne({ sessionId: "..." })
     */
    Optional<Cart> findBySessionId(String sessionId);

    /**
     * Delete a cart by session ID.
     * Used when clearing the entire cart (checkout completed or user action).
     *
     * → db.carts.deleteOne({ sessionId: "..." })
     */
    void deleteBySessionId(String sessionId);
}
