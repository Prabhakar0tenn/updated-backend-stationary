package com.stationary.stationary_backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * CartResponse — Full cart state returned by cart endpoints.
 *
 * Includes:
 *   - sessionId: client echoes this back in subsequent requests
 *   - items: list of CartItemResponse (product snapshot + quantity + lineTotal)
 *   - totalItems: total quantity across all items (for cart badge count)
 *   - grandTotal: sum of all lineTotals (for checkout summary)
 *   - whatsappMessage: pre-formatted order message ready to open in WhatsApp
 *   - lastModifiedAt: timestamp for freshness display
 */
@Data
@Builder
public class CartResponse {

    private String sessionId;
    private List<CartItemResponse> items;

    /** Total number of units across all items (e.g., 3 pens + 2 notebooks = 5). */
    private Integer totalItems;

    /** Sum of all (price × quantity). */
    private Double grandTotal;

    /**
     * Pre-formatted WhatsApp message combining all items.
     * Frontend uses this to open: https://wa.me/91XXXXXXXXXX?text=...
     * Format:
     *   🛒 New Order:
     *   1. Blue Pen × 2 = ₹120
     *   2. A4 Notebook × 1 = ₹60
     *   ──────────────────
     *   Total: ₹180
     */
    private String whatsappMessage;

    private Instant lastModifiedAt;
}
