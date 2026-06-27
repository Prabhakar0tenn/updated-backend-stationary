package com.stationary.stationary_backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JwtFilter — Intercepts every HTTP request to validate the JWT.
 *
 * ──────────────────────────────────────────────────────────
 * WHY extend OncePerRequestFilter?
 * ──────────────────────────────────────────────────────────
 * Spring's filter chain can invoke filters multiple times
 * in some scenarios (e.g., async dispatch, error dispatch).
 * OncePerRequestFilter guarantees this filter runs EXACTLY
 * once per request, regardless of how Spring dispatches it.
 *
 * ──────────────────────────────────────────────────────────
 * HOW THE FILTER FITS IN THE REQUEST LIFECYCLE:
 * ──────────────────────────────────────────────────────────
 *   Browser request
 *       ↓
 *   JwtFilter (us)
 *       ↓ reads Authorization header
 *       ↓ validates token via JwtUtil
 *       ↓ sets SecurityContext if valid
 *       ↓
 *   SecurityConfig rules check
 *       ↓ is this route ADMIN-only? Is user authenticated?
 *       ↓
 *   Controller method
 *
 * ──────────────────────────────────────────────────────────
 * WHAT IS SecurityContext?
 * ──────────────────────────────────────────────────────────
 * Spring Security stores "who is making this request" in the
 * SecurityContext (a thread-local variable — one per request).
 * If it's empty, Spring treats the request as anonymous.
 * If it has an authenticated user, protected routes are accessible.
 *
 * ──────────────────────────────────────────────────────────
 * FUTURE (v2 — CUSTOMER auth):
 * ──────────────────────────────────────────────────────────
 * This filter reads the role from the token ("ROLE_ADMIN" or
 * "ROLE_CUSTOMER") and sets it as a GrantedAuthority.
 * SecurityConfig then uses .hasRole("ADMIN") or .hasRole("CUSTOMER").
 * This filter doesn't change AT ALL for v2.
 */
@Component
@RequiredArgsConstructor
@Slf4j  // WHY @Slf4j? Lombok generates: private static final Logger log = ...
        // Saves you from writing that boilerplate in every class.
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        // ── Step 1: Check if Authorization header exists and has Bearer prefix ──
        // WHY check for "Bearer "? The HTTP spec for JWT is:
        //   Authorization: Bearer <token>
        // If the header is missing or doesn't start with "Bearer ",
        // this is either a public request or a malformed request.
        // We skip JWT processing and let SecurityConfig decide.
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response); // pass through unchanged
            return;
        }

        // ── Step 2: Extract the raw token string ──
        // "Bearer " is 7 characters — substring(7) removes it
        final String token = authHeader.substring(7);

        // ── Step 3: Validate the token ──
        if (!jwtUtil.isTokenValid(token)) {
            // Don't throw here — just don't authenticate.
            // SecurityConfig will reject protected routes if context is empty.
            // WHY not throw? Throwing here returns a 500. We want a 401.
            // The 401 is handled by the AuthenticationEntryPoint in SecurityConfig.
            log.warn("Invalid JWT token on request to: {}", request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        // ── Step 4: Check if already authenticated ──
        // WHY this check? If another filter already set authentication
        // (unlikely but possible), we don't overwrite it.
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        // ── Step 5: Extract claims and build authentication object ──
        String adminId = jwtUtil.extractAdminId(token);
        String role    = jwtUtil.extractRole(token);    // e.g. "ROLE_ADMIN"
        String username = jwtUtil.extractUsername(token);

        // SimpleGrantedAuthority wraps the role string.
        // Spring Security requires the "ROLE_" prefix for .hasRole() checks.
        // If you stored "ROLE_ADMIN" in the token, this works directly.
        var authority = new SimpleGrantedAuthority(role);

        // UsernamePasswordAuthenticationToken is Spring's standard auth object.
        // Constructor: (principal, credentials, authorities)
        //   principal   = who they are (we use adminId as the unique identifier)
        //   credentials = null (we already verified via JWT — no password needed)
        //   authorities = what they can do (their roles)
        var authentication = new UsernamePasswordAuthenticationToken(
                adminId,    // principal — controllers can call getPrincipal() to get adminId
                null,       // credentials — cleared after authentication
                List.of(authority)
        );

        // Attach request details (IP, session) to the auth object — useful for audit logs
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        // ── Step 6: Set the authentication in SecurityContext ──
        // From this point forward, this request is authenticated.
        // Any controller can call:
        //   SecurityContextHolder.getContext().getAuthentication().getPrincipal()
        // to get the adminId.
        SecurityContextHolder.getContext().setAuthentication(authentication);

        log.debug("Authenticated admin: {} on {}", username, request.getRequestURI());

        // Pass request to the next filter (and eventually the controller)
        filterChain.doFilter(request, response);
    }
}
