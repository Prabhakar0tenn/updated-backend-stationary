package com.stationary.stationary_backend.service;

import com.stationary.stationary_backend.model.Admin;
import com.stationary.stationary_backend.repository.AdminRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * AdminSeedRunner — Creates the initial admin account on application startup.
 *
 * ──────────────────────────────────────────────────────────
 * WHY ApplicationRunner (not @PostConstruct or CommandLineRunner)?
 * ──────────────────────────────────────────────────────────
 * @PostConstruct → runs during bean initialization, BEFORE Spring context
 *                  is fully ready. DB connections may not be up yet. Risky.
 *
 * CommandLineRunner → identical to ApplicationRunner but receives String[]
 *                     args instead of ApplicationArguments. Simpler but less
 *                     powerful. Either works for us.
 *
 * ApplicationRunner → runs AFTER the entire Spring context is initialized.
 *                     All beans, DB connections, and configs are ready.
 *                     This is the correct choice for seeding.
 *
 * ──────────────────────────────────────────────────────────
 * WHY IDEMPOTENT?
 * ──────────────────────────────────────────────────────────
 * Render restarts your app on every deploy.
 * Without the existence check, every deploy would try to insert
 * a duplicate admin → MongoDB unique constraint error → app crash.
 *
 * Idempotent = safe to run multiple times with the same result.
 * This runner: "only create admin if none exists" → truly idempotent.
 *
 * ──────────────────────────────────────────────────────────
 * WHY env vars instead of hardcoded credentials?
 * ──────────────────────────────────────────────────────────
 * Hardcoded "admin/password123" in code = terrible security.
 * Anyone who reads your GitHub repo has your admin password.
 * Env vars stay out of Git. Set them on Render dashboard.
 *
 * application.yml defaults (for local dev ONLY):
 *   admin.seed.username → "admin"
 *   admin.seed.password → "changeme123"
 *   admin.seed.email    → "admin@stationary.com"
 *
 * REMINDER: Change these defaults before pushing to production.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminSeedRunner implements ApplicationRunner {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.seed.username}")
    private String seedUsername;

    @Value("${admin.seed.password}")
    private String seedPassword;

    @Value("${admin.seed.email}")
    private String seedEmail;

    @Override
    public void run(ApplicationArguments args) {
        // ── Idempotency check ──────────────────────────────────────────────
        // "Does an admin with this username already exist?"
        // If yes → skip. This makes the runner safe to call on every restart.
        if (adminRepository.existsByUsername(seedUsername)) {
            log.info("Admin seed skipped — admin '{}' already exists", seedUsername);
            return;
        }

        // ── Build the admin entity ─────────────────────────────────────────
        // WHY not use Admin.builder().password(seedPassword)?
        // Because passwordHash is what we store — never raw password.
        // passwordEncoder.encode() runs BCrypt(10) — ~100ms intentionally.
        // This only runs once at startup, so the delay is irrelevant.
        Admin admin = Admin.builder()
                .username(seedUsername)
                .email(seedEmail)
                .passwordHash(passwordEncoder.encode(seedPassword))
                .isActive(true)
                .build();

        adminRepository.save(admin);

        // WHY not log the password?
        // NEVER log credentials — not even in development.
        // Log files can be shared, stored in monitoring tools, etc.
        log.info("Admin seeded successfully — username: '{}'", seedUsername);
        log.info("REMINDER: Change ADMIN_PASSWORD env var before production deploy!");
    }
}
