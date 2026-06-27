package com.stationary.stationary_backend.controller;

import com.stationary.stationary_backend.dto.response.ApiResponse;
import com.stationary.stationary_backend.dto.shared.ImageDto;
import com.stationary.stationary_backend.service.ImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * ImageController — Handles Cloudinary image operations.
 *
 * ──────────────────────────────────────────────────────────
 * ENDPOINTS:
 * ──────────────────────────────────────────────────────────
 *   POST   /api/v1/images/upload       [ADMIN] — upload image, returns publicId + url
 *   DELETE /api/v1/images/{publicId}   [ADMIN] — delete image from Cloudinary
 *
 * ──────────────────────────────────────────────────────────
 * ADMIN UPLOAD FLOW:
 * ──────────────────────────────────────────────────────────
 *   Step 1: Admin selects image in product form
 *   Step 2: React sends: POST /api/v1/images/upload (multipart/form-data)
 *           Form field name: "file"
 *   Step 3: Backend uploads to Cloudinary → returns { publicId, url }
 *   Step 4: Admin fills rest of product form and submits
 *   Step 5: ProductRequest.image = { publicId, url } from step 3
 *   Step 6: POST /api/v1/products — product saved with those image values
 *
 * WHY upload image BEFORE creating the product?
 * Separating upload from product creation means:
 *   - If the product form fails validation, the image is still uploaded
 *     (minor orphan risk, acceptable for v1)
 *   - Image upload progress can be shown in the UI independently
 *   - Image can be re-used/previewed before submitting the full form
 *
 * v2 improvement: add a cleanup job for orphaned Cloudinary images.
 *
 * ──────────────────────────────────────────────────────────
 * consumes = MediaType.MULTIPART_FORM_DATA_VALUE
 * ──────────────────────────────────────────────────────────
 * WHY explicit consumes? It documents the endpoint requires multipart.
 * Without it, Spring infers from the @RequestParam MultipartFile — but
 * being explicit makes Postman/Swagger aware of the content type.
 * Frontend must set Content-Type: multipart/form-data (axios does this
 * automatically when you pass FormData as the request body).
 *
 * ──────────────────────────────────────────────────────────
 * publicId encoding in DELETE:
 * ──────────────────────────────────────────────────────────
 * Cloudinary publicIds look like: "stationary-products/xyzabc123"
 * The "/" is URL-encoded as "%2F" in path variables.
 * If the frontend sends the raw publicId, Spring decodes it automatically.
 * But to be safe, the frontend should URL-encode the publicId before
 * appending to the path. Or use a request body for DELETE.
 *
 * v1 simplification: use path variable. Works for simple publicIds.
 * v2: switch to request body if publicIds contain complex characters.
 */
@RestController
@RequestMapping("/api/v1/images")
@RequiredArgsConstructor
public class ImageController {

    private final ImageService imageService;

    /**
     * Upload a product image to Cloudinary.
     * ADMIN only.
     *
     * Request: multipart/form-data with field "file" (JPEG/PNG/WebP, max 10MB)
     * Response: { "publicId": "stationary-products/abc", "url": "https://res.cloudinary.com/..." }
     *
     * Frontend (axios):
     *   const formData = new FormData();
     *   formData.append('file', file);
     *   const { data } = await axios.post('/api/v1/images/upload', formData);
     *   const { publicId, url } = data.data;
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ImageDto>> upload(
            @RequestParam("file") MultipartFile file) {

        ImageDto imageDto = imageService.upload(file);
        return ResponseEntity.ok(ApiResponse.of("Image uploaded successfully", imageDto));
    }

    /**
     * Delete an image from Cloudinary.
     * ADMIN only.
     * Used when: admin replaces a category image, or cleans up orphaned uploads.
     *
     * Note: Product image deletion during product delete/update is handled
     * automatically inside ProductService — you don't need to call this endpoint
     * for product operations. This endpoint is for manual cleanup.
     *
     * @param publicId Cloudinary public_id — URL-encode the "/" as "%2F"
     *                 Example: DELETE /api/v1/images/stationary-products%2Fabc123
     */
    @DeleteMapping("/{publicId}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String publicId) {
        imageService.delete(publicId);
        return ResponseEntity.ok(ApiResponse.ok("Image deleted successfully"));
    }
}
