package com.stationary.stationary_backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * RefreshRequest — Body for POST /api/v1/auth/refresh
 *
 * WHY a request body and not a query param?
 * Query params appear in server logs and browser history.
 * A refresh token in a query param = token leaked to logs.
 * Request body is never logged by default. More secure.
 *
 * Alternative: HttpOnly cookie (more secure but more complex).
 * For v1 admin-only, request body is acceptable.
 */
@Data
public class RefreshRequest {

    @NotBlank(message = "Refresh token is required")
    private String refreshToken;
}
