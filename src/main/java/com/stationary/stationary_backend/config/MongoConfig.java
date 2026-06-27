package com.stationary.stationary_backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

/**
 * MongoConfig — Enables MongoDB auditing.
 *
 * ──────────────────────────────────────────────────────────
 * WHY a separate config class just for one annotation?
 * ──────────────────────────────────────────────────────────
 * @EnableMongoAuditing activates the mechanism that auto-populates
 * @CreatedDate and @LastModifiedDate fields on your entities.
 *
 * Without this:
 *   product.createdAt → always null (annotation does nothing)
 *   product.updatedAt → always null
 *
 * With this:
 *   product.createdAt → set on first save, never changed again
 *   product.updatedAt → updated on every save automatically
 *
 * WHY not put it on SecurityConfig or another existing config?
 * Single Responsibility: one config class, one concern.
 * MongoConfig = MongoDB-specific setup.
 * SecurityConfig = security rules only.
 * Mixing them makes both harder to understand and maintain.
 *
 * ──────────────────────────────────────────────────────────
 * HOW IT WORKS INTERNALLY:
 * ──────────────────────────────────────────────────────────
 * Spring Data MongoDB registers an AuditingEntityListener.
 * Before every save(), this listener checks for @CreatedDate
 * and @LastModifiedDate and fills them with Instant.now().
 * For @CreatedDate: only fills if the field is currently null
 * (i.e., new document). Never overwrites existing value.
 * For @LastModifiedDate: always updates to Instant.now().
 *
 * This saves you from writing this in every service method:
 *   product.setUpdatedAt(Instant.now());
 * DRY (Don't Repeat Yourself).
 */
@Configuration
@EnableMongoAuditing
public class MongoConfig {
    // No bean definitions needed — the annotation does all the work.
}
