package com.stationary.stationary_backend.config;

import com.stationary.stationary_backend.security.JwtFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * SecurityConfig — The single source of truth for HTTP security.
 *
 * ──────────────────────────────────────────────────────────
 * WHY @Configuration + @EnableWebSecurity?
 * ──────────────────────────────────────────────────────────
 * @Configuration tells Spring: "this class has @Bean methods,
 * treat them as Spring-managed beans."
 * @EnableWebSecurity activates Spring Security's web support,
 * replacing the default "lock everything behind a login form."
 *
 * ──────────────────────────────────────────────────────────
 * DESIGN PRINCIPLE: Defense in Depth
 * ──────────────────────────────────────────────────────────
 * Security is layered:
 *   Layer 1 → CORS: rejects cross-origin requests from unknown origins
 *   Layer 2 → JwtFilter: validates tokens on every request
 *   Layer 3 → SecurityFilterChain rules: authorizes specific routes
 *   Layer 4 → @PreAuthorize (optional, add in v2 for fine-grained control)
 *
 * If any layer blocks the request, it never reaches the controller.
 *
 * ──────────────────────────────────────────────────────────
 * FUTURE (v2 — CUSTOMER auth):
 * ──────────────────────────────────────────────────────────
 * Add customer routes here with .hasRole("CUSTOMER").
 * The JwtFilter already reads role from token — no filter changes.
 * Just add new rules in authorizeHttpRequests() below.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    @Value("${app.cors.allowed-origins}")
    private List<String> allowedOrigins;

    /**
     * The Security Filter Chain — defines ALL security rules.
     *
     * This is a @Bean, meaning Spring replaces its own default
     * security config with this one.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // ── CSRF: Disabled ─────────────────────────────────────
            // WHY disable CSRF?
            // CSRF attacks exploit browser cookie auto-sending.
            // We use Authorization headers (Bearer token), not cookies.
            // Browsers don't auto-send headers → CSRF doesn't apply.
            // NEVER disable CSRF in a form-based login app (JSP, Thymeleaf).
            // For REST APIs with JWT headers: safe to disable.
            .csrf(AbstractHttpConfigurer::disable)

            // ── CORS ───────────────────────────────────────────────
            // WHY configure CORS in Spring Security, not just in a @Bean?
            // Spring Security processes requests BEFORE your controller.
            // If CORS isn't configured here, preflight OPTIONS requests
            // get blocked by the security filter before CORS can respond.
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // ── Session: Stateless ─────────────────────────────────
            // WHY STATELESS?
            // Traditional Spring Security stores authenticated user in
            // HttpSession (server-side state). With JWTs we don't need
            // sessions — every request carries its own credentials.
            // STATELESS = Spring will never create an HttpSession.
            // This is REQUIRED for JWT auth to work correctly.
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // ── 401 vs 403 ─────────────────────────────────────────
            // WHY configure exceptionHandling?
            // By default, unauthenticated requests get redirected to /login
            // (Spring Security's built-in login form). We don't have a form.
            // We want a 401 JSON response. HttpStatusEntryPoint does that.
            //
            // 401 = "Who are you? Please authenticate." (missing/invalid token)
            // 403 = "I know who you are, but you're not allowed here." (wrong role)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))

            // ── Route Authorization Rules ──────────────────────────
            // ORDER MATTERS: more specific rules FIRST, more general LAST.
            // Spring evaluates from top to bottom and stops at first match.
            .authorizeHttpRequests(auth -> auth

                // ── PUBLIC auth endpoints ────────────────────────
                .requestMatchers("/api/v1/auth/login").permitAll()
                .requestMatchers("/api/v1/auth/refresh").permitAll()

                // ── PUBLIC Actuator health check ─────────────────
                .requestMatchers("/api/v1/actuator/health").permitAll()

                // ── ADMIN endpoints with GET method ──────────────
                // These must come BEFORE general GET permitAll rules to enforce ADMIN role.
                .requestMatchers(HttpMethod.GET, "/api/v1/products/admin").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/categories/admin").hasRole("ADMIN")

                // ── PUBLIC GET endpoints ─────────────────────────
                // Anyone can browse products and categories — no login needed.
                .requestMatchers(HttpMethod.GET, "/api/v1/products/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/categories/**").permitAll()

                // ── ADMIN-only write/delete routes ───────────────
                .requestMatchers("/api/v1/products/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/categories/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/images/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/auth/logout").hasRole("ADMIN")

                // ── PUBLIC Cart endpoints (no auth needed for shoppers) ──
                .requestMatchers("/api/v1/cart/**").permitAll()

                // ── Everything else: deny ────────────────────────
                .anyRequest().authenticated()
            )

            // ── JWT Filter Registration ────────────────────────────
            // WHY addFilterBefore?
            // We want JwtFilter to run BEFORE Spring's default
            // UsernamePasswordAuthenticationFilter.
            // That default filter handles form-based login (not what we want).
            // Our JwtFilter runs first, sets SecurityContext,
            // and then UsernamePasswordAuthenticationFilter has nothing to do.
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * BCryptPasswordEncoder — for hashing admin passwords.
     *
     * WHY BCrypt over MD5/SHA?
     * BCrypt is designed to be SLOW (intentionally).
     * Strength 10 = 2^10 = 1024 rounds of hashing.
     * A brute-force attacker can only try ~100 passwords/sec vs
     * millions/sec with MD5. Slowing down the attacker is the goal.
     *
     * WHY a @Bean?
     * So Spring can inject PasswordEncoder wherever needed
     * (AdminSeedRunner, AuthService) without coupling them to BCrypt.
     * If you ever switch to Argon2, you change this one bean.
     *
     * NEVER use MD5, SHA-1, or SHA-256 for passwords.
     * They are designed to be FAST — the opposite of what you want.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    /**
     * UserDetailsService — No-op bean to suppress Spring Security's
     * auto-generated password warning on startup.
     *
     * WHY do we need this?
     * Spring Security's UserDetailsServiceAutoConfiguration kicks in when
     * it can't find a UserDetailsService bean. It creates an in-memory user
     * with a random password and logs a WARN. This is ugly in logs.
     *
     * We use JWT, not Spring's form-based login — so we don't actually use
     * UserDetailsService for authentication. But declaring this empty bean
     * tells Spring "I've got it covered" and silences the auto-config.
     *
     * The InMemoryUserDetailsManager with no users = nobody can use it.
     * It's purely a placeholder to satisfy the framework requirement.
     */
    @Bean
    public UserDetailsService userDetailsService() {
        // No users — this service is never actually used.
        // Authentication is handled by JwtFilter + AuthService.
        return new InMemoryUserDetailsManager();
    }

    /**
     * CORS Configuration.
     *
     * WHY explicitly list allowed origins (not wildcard *)?
     * Wildcard "*" disallows sending credentials (cookies, auth headers)
     * from the browser. Since we need the Authorization header,
     * we must list exact origins.
     *
     * FUTURE: Add your Vercel URL to CORS_ALLOWED_ORIGINS env var
     * when deploying. No code changes needed.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Combine env allowed-origins with wildcards for localhost and Vercel deployments
        java.util.List<String> patterns = new java.util.ArrayList<>();
        patterns.add("http://localhost:[*]");
        patterns.add("https://*.vercel.app");
        if (allowedOrigins != null) {
            patterns.addAll(allowedOrigins);
        }
        config.setAllowedOriginPatterns(patterns);
        
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Session-Id"));
        config.setExposedHeaders(List.of("Authorization", "X-Session-Id")); // headers frontend can read
        config.setAllowCredentials(true);  // required for Authorization header to work
        config.setMaxAge(3600L);           // cache preflight response for 1 hour

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
