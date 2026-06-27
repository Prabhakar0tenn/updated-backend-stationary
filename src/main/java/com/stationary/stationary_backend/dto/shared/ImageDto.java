package com.stationary.stationary_backend.dto.shared;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ImageDto — Shared DTO for image data (publicId + url).
 *
 * ──────────────────────────────────────────────────────────
 * WHY in a 'shared' sub-package?
 * ──────────────────────────────────────────────────────────
 * ImageDto is used in BOTH directions:
 *   Request:  ProductRequest.image (admin sends Cloudinary info)
 *   Response: ProductResponse.image (client receives the URL)
 *
 * Putting it in dto/request/ would be wrong (it's also in responses).
 * Putting it in dto/response/ would be wrong (it's also in requests).
 * dto/shared/ = "used by both request and response DTOs"
 *
 * Alternative: duplicate it as ImageRequestDto and ImageResponseDto.
 * Unnecessary for a class this simple — same fields in both directions.
 * If they diverge (e.g., response needs width/height, request doesn't),
 * split them then.
 *
 * ──────────────────────────────────────────────────────────
 * This mirrors the ImageData model but is NOT the same class:
 * ──────────────────────────────────────────────────────────
 * ImageData (model/embedded) = MongoDB document shape
 * ImageDto  (dto/shared)     = HTTP transport shape
 *
 * They happen to have the same fields right now, but:
 *   - ImageData is for persistence (Spring Data reads/writes it)
 *   - ImageDto is for transport (Jackson serializes/deserializes it)
 *
 * Why keep them separate? If you add an internal field to ImageData
 * (e.g., a processing flag), it shouldn't leak into the API response.
 * MapStruct handles the conversion between them.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageDto {

    /**
     * Cloudinary asset identifier.
     * Required — needed to delete the image from Cloudinary later.
     * Example: "stationery/products/abc123"
     */
    @NotBlank(message = "Image publicId is required")
    private String publicId;

    /**
     * CDN delivery URL. Always HTTPS.
     * Example: "https://res.cloudinary.com/your-cloud/image/upload/..."
     * This is what the frontend renders in <img src="...">
     */
    @NotBlank(message = "Image URL is required")
    private String url;
}
