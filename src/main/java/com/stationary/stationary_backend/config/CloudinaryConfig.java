package com.stationary.stationary_backend.config;

import com.cloudinary.Cloudinary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * CloudinaryConfig — Wires the Cloudinary SDK as a Spring bean.
 *
 * ──────────────────────────────────────────────────────────
 * WHY a @Bean instead of new Cloudinary() in ImageService?
 * ──────────────────────────────────────────────────────────
 * If you do `new Cloudinary()` inside ImageService, you:
 *   1. Hard-couple ImageService to Cloudinary (hard to test)
 *   2. Create a new SDK instance every time (wasteful)
 *   3. Have no central place to configure it
 *
 * As a @Bean: Spring creates ONE instance at startup,
 * injects it wherever needed, and it's easy to mock in tests.
 *
 * ──────────────────────────────────────────────────────────
 * WHAT CLOUDINARY NEEDS:
 * ──────────────────────────────────────────────────────────
 *   cloud_name — your account's cloud identifier
 *   api_key    — public identifier for API calls
 *   api_secret — private key that signs requests (NEVER expose to frontend)
 *
 * These come from Cloudinary Dashboard → Settings → API Keys.
 *
 * ──────────────────────────────────────────────────────────
 * SECURITY NOTE:
 * ──────────────────────────────────────────────────────────
 * api_secret must NEVER reach the browser.
 * That's why upload goes: browser → our backend → Cloudinary.
 * The browser only ever sees the final CDN URL.
 *
 * ──────────────────────────────────────────────────────────
 * FUTURE:
 * ──────────────────────────────────────────────────────────
 * If you want to support signed uploads with expiry (for
 * large files), you generate a signature here on the server
 * and give it to the frontend. Same bean, new method in ImageService.
 */
@Configuration
public class CloudinaryConfig {

    @Bean
    public Cloudinary cloudinary(
            @Value("${cloudinary.cloud-name}") String cloudName,
            @Value("${cloudinary.api-key}")    String apiKey,
            @Value("${cloudinary.api-secret}") String apiSecret
    ) {
        return new Cloudinary(Map.of(
                "cloud_name", cloudName,
                "api_key",    apiKey,
                "api_secret", apiSecret,
                "secure",     true  // WHY? Always use HTTPS URLs. Never HTTP for images.
        ));
    }
}
