package com.stationary.stationary_backend.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.stationary.stationary_backend.dto.shared.ImageDto;
import com.stationary.stationary_backend.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * ImageService — Handles Cloudinary image uploads and deletions.
 *
 * ──────────────────────────────────────────────────────────
 * UPLOAD FLOW:
 * ──────────────────────────────────────────────────────────
 *   1. Admin selects image in React admin panel
 *   2. React sends multipart/form-data to POST /api/v1/images/upload
 *   3. ImageService.upload() sends it to Cloudinary servers
 *   4. Cloudinary responds with { public_id, secure_url, ... }
 *   5. We return { publicId, url } to the frontend
 *   6. Frontend puts those values in the ProductRequest.image field
 *   7. POST /api/v1/products saves the URL + publicId to MongoDB
 *
 * WHY not upload directly from browser to Cloudinary?
 * Browser → Cloudinary uploads use "unsigned presets" which expose
 * your Cloudinary credentials client-side. Anyone can inspect
 * DevTools, get your API key, and spam your Cloudinary account.
 * Backend upload keeps api_secret server-side. Always.
 *
 * ──────────────────────────────────────────────────────────
 * DELETE FLOW:
 * ──────────────────────────────────────────────────────────
 * When admin replaces a product image:
 *   1. Upload new image → get new publicId + url
 *   2. PUT /api/v1/products/{id} with new image data
 *   3. ProductService detects image changed → calls ImageService.delete(oldPublicId)
 *   4. Old image removed from Cloudinary storage
 *
 * WHY delete old images? Unused images in Cloudinary cost storage.
 * They add up. Cleaning up on replace is professional hygiene.
 *
 * ──────────────────────────────────────────────────────────
 * WHY store publicId in MongoDB (not just the URL)?
 * ──────────────────────────────────────────────────────────
 * To DELETE an image from Cloudinary, you need the publicId.
 * The URL alone is not enough. We store both:
 *   url       → used by frontend to display image
 *   publicId  → used by backend to delete from Cloudinary
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImageService {

    private final Cloudinary cloudinary;

    // The Cloudinary folder where all product images are organized
    private static final String FOLDER = "stationary-products";

    // Allowed MIME types — reject non-image uploads early
    private static final java.util.Set<String> ALLOWED_TYPES = java.util.Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp"
    );

    /**
     * Upload a product image to Cloudinary.
     *
     * @param file multipart file from admin panel
     * @return ImageDto with { publicId, url }
     */
    public ImageDto upload(MultipartFile file) {
        // ── Validate file ─────────────────────────────────────────────────
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Please select an image file to upload");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
            throw new BadRequestException(
                    "Invalid file type. Only JPEG, PNG, and WebP images are allowed");
        }

        // ── Upload to Cloudinary ──────────────────────────────────────────
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> uploadResult = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", FOLDER,
                            // transformation: auto quality + auto format
                            // WHY? Cloudinary converts to WebP for modern browsers → smaller files.
                            // quality: auto → Cloudinary finds smallest size without visible loss.
                            "quality", "auto",
                            "fetch_format", "auto"
                    )
            );

            String publicId = (String) uploadResult.get("public_id");
            String secureUrl = (String) uploadResult.get("secure_url");

            log.info("Image uploaded to Cloudinary: publicId={}", publicId);

            return ImageDto.builder()
                    .publicId(publicId)
                    .url(secureUrl)
                    .build();

        } catch (IOException e) {
            log.error("Cloudinary upload failed: {}", e.getMessage(), e);
            throw new BadRequestException("Image upload failed. Please try again.");
        }
    }

    /**
     * Delete an image from Cloudinary by its publicId.
     *
     * WHY not throw on failure?
     * If Cloudinary delete fails (network blip, already deleted),
     * the product operation already succeeded. Don't rollback a successful
     * product update just because image cleanup failed.
     * Log the failure for manual cleanup later.
     *
     * @param publicId Cloudinary public_id of the image to delete
     */
    public void delete(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return;  // nothing to delete
        }
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
            log.info("Image deleted from Cloudinary: publicId={}", publicId);
        } catch (IOException e) {
            // Non-fatal: log and continue. Don't block product operations.
            log.warn("Failed to delete image from Cloudinary (publicId={}): {}", publicId, e.getMessage());
        }
    }
}
