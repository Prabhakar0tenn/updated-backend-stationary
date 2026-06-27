package com.stationary.stationary_backend.model.embedded;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ImageData — Embedded document for product image metadata.
 *
 * ──────────────────────────────────────────────────────────
 * WHY a separate class instead of two String fields?
 * ──────────────────────────────────────────────────────────
 * Option A (what many freshers do):
 *   private String imageUrl;
 *   private String imagePublicId;
 *
 * Option B (what we do):
 *   private ImageData image;  ← embedded object
 *
 * With Option A, adding a third image property (e.g., altText,
 * width, height) means adding more fields directly on Product —
 * polluting a class that already has 10+ fields.
 *
 * With Option B, all image concerns are grouped in ImageData.
 * Product stays clean. ImageData can evolve independently.
 *
 * ──────────────────────────────────────────────────────────
 * HOW it's stored in MongoDB:
 * ──────────────────────────────────────────────────────────
 * MongoDB stores this as a nested document inside the product:
 *   {
 *     "_id": "...",
 *     "name": "Blue Pen",
 *     "image": {
 *       "publicId": "stationery/products/abc123",
 *       "url": "https://res.cloudinary.com/..."
 *     }
 *   }
 *
 * This is called an "embedded document" — no separate collection,
 * no join needed. Fast reads, atomic saves.
 *
 * ──────────────────────────────────────────────────────────
 * WHY store publicId?
 * ──────────────────────────────────────────────────────────
 * Cloudinary deletion requires the publicId, not the URL.
 * URL can change (if you rename the file or transform it).
 * PublicId is stable — it's Cloudinary's internal identifier.
 * Without it, you cannot delete the old image when admin
 * uploads a new one → orphaned images accumulate forever.
 *
 * ──────────────────────────────────────────────────────────
 * WHY @Builder?
 * ──────────────────────────────────────────────────────────
 * Lets you write:
 *   ImageData.builder().publicId("abc").url("https://...").build()
 * Much more readable than:
 *   new ImageData("abc", "https://...")
 * Especially when adding more fields in the future.
 *
 * ──────────────────────────────────────────────────────────
 * FUTURE (v2):
 * ──────────────────────────────────────────────────────────
 * If you need multiple images per product, change Product's
 *   private ImageData image;
 * to:
 *   private List<ImageData> images;
 * This class doesn't change at all. That's good design.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageData {

    /**
     * Cloudinary's asset identifier.
     * Example: "stationery/products/abc123"
     * Used for: deletion, transformation, signed URLs.
     */
    private String publicId;

    /**
     * CDN delivery URL (HTTPS always).
     * Example: "https://res.cloudinary.com/your-cloud/image/upload/v1234/stationery/products/abc123.jpg"
     * Used for: displaying the image in the frontend.
     */
    private String url;
}
