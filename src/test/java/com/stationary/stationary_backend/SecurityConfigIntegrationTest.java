package com.stationary.stationary_backend;

import com.stationary.stationary_backend.config.SecurityConfig;
import com.stationary.stationary_backend.controller.AuthController;
import com.stationary.stationary_backend.controller.CategoryController;
import com.stationary.stationary_backend.controller.ImageController;
import com.stationary.stationary_backend.controller.ProductController;
import com.stationary.stationary_backend.security.JwtFilter;
import com.stationary.stationary_backend.security.JwtUtil;
import com.stationary.stationary_backend.service.AuthService;
import com.stationary.stationary_backend.service.CategoryService;
import com.stationary.stationary_backend.service.ImageService;
import com.stationary.stationary_backend.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        ProductController.class,
        CategoryController.class,
        AuthController.class,
        ImageController.class
})
@Import({SecurityConfig.class, JwtFilter.class, JwtUtil.class})
public class SecurityConfigIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    // Mock controller dependencies
    @MockBean
    private ProductService productService;

    @MockBean
    private CategoryService categoryService;

    @MockBean
    private AuthService authService;

    @MockBean
    private ImageService imageService;

    @Test
    public void testPublicEndpoints_Success() throws Exception {
        // Public GET products/categories should be allowed (200 OK)
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk());
    }

    @Test
    public void testPublicActuator_Success() throws Exception {
        // GET /api/v1/actuator/health should be allowed for anyone security-wise.
        // Since Actuator auto-configuration is not loaded in the @WebMvcTest slice,
        // it throws NoResourceFoundException (500) rather than 401 Unauthorized.
        // Getting a 500 proves that security allowed the request to bypass the filter.
        mockMvc.perform(get("/api/v1/actuator/health"))
                .andExpect(status().is5xxServerError());
    }

    @Test
    public void testAdminGetEndpoints_Anonymous_Unauthorized() throws Exception {
        // Admin GET listings should be blocked without credentials (401)
        mockMvc.perform(get("/api/v1/products/admin"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/categories/admin"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void testAdminWriteEndpoints_Anonymous_Unauthorized() throws Exception {
        // Admin POST actions should be blocked without credentials (401)
        mockMvc.perform(post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void testAdminEndpoints_WithAdminToken_Success() throws Exception {
        // Generate a mock ROLE_ADMIN JWT token
        String token = jwtUtil.generateToken("mock-admin-id", "admin", "ROLE_ADMIN");

        // Should pass security filter and reach the controller (returning 200 OK)
        mockMvc.perform(get("/api/v1/products/admin")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/categories/admin")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    public void testAdminEndpoints_WithInvalidToken_Unauthorized() throws Exception {
        // Bad token is rejected (401)
        mockMvc.perform(get("/api/v1/products/admin")
                .header("Authorization", "Bearer badtoken123"))
                .andExpect(status().isUnauthorized());
    }
}
