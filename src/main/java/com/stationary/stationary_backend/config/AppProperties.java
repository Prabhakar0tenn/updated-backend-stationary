package com.stationary.stationary_backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AppProperties — Typed configuration binding.
 *
 * WHY use @ConfigurationProperties instead of @Value?
 * ────────────────────────────────────────────────────
 * @Value("${jwt.secret}") works but has problems:
 *   1. Strings scattered across dozens of classes — no central view.
 *   2. No type safety — everything is String, typos fail at runtime.
 *   3. No IDE autocomplete for your own config keys.
 *
 * @ConfigurationProperties binds entire YAML subtrees to a Java class.
 * You get: type safety, IDE support, validation, one place to look.
 *
 * FUTURE (v2):
 * When customer features need their own config (e.g., OTP expiry),
 * add a nested static class here. Zero impact on existing code.
 *
 * HOW it works:
 * @ConfigurationProperties(prefix = "jwt") maps:
 *   jwt.secret            → this.jwt.secret
 *   jwt.access-token-expiry-ms → this.jwt.accessTokenExpiryMs
 * Spring auto-converts kebab-case → camelCase.
 */
@Component
@ConfigurationProperties(prefix = "app")  // binds app.* from application.yml
@Getter
@Setter
public class AppProperties {

    private Cors cors = new Cors();

    @Getter
    @Setter
    public static class Cors {
        /**
         * Comma-separated list of allowed origins.
         * Populated from CORS_ALLOWED_ORIGINS env var.
         * Example: http://localhost:5173,https://your-app.vercel.app
         */
        private List<String> allowedOrigins;
    }
}
