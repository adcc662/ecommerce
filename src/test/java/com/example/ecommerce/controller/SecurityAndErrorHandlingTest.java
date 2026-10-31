package com.example.ecommerce.controller;

import com.example.ecommerce.config.SecurityConfig;
import com.example.ecommerce.security.JwtAuthenticationFilter;
import com.example.ecommerce.security.JwtUtil;
import com.example.ecommerce.service.AuthService;
import com.example.ecommerce.service.ProductService;
import com.example.ecommerce.service.ShoppingCartService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AuthController.class, ProductController.class, ShoppingCartController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtUtil.class})
@TestPropertySource(properties = {
        "jwt.secret=" + SecurityAndErrorHandlingTest.SECRET,
        "jwt.expiration=60000",
        "app.cors.allowed-origins=http://localhost:3000"
})
class SecurityAndErrorHandlingTest {

    static final String SECRET = "test-only-secret-0123456789abcdef0123456789abcdef";
    private static final String USER_EMAIL = "user@example.com";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtUtil jwtUtil;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private ShoppingCartService shoppingCartService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        when(userDetailsService.loadUserByUsername(USER_EMAIL)).thenReturn(
                User.withUsername(USER_EMAIL).password("x").roles("USER").build());
    }

    private String userToken() {
        return "Bearer " + jwtUtil.generateToken(USER_EMAIL);
    }

    // S3: client errors never surface as 500

    @Test
    void wrongCredentialsReturn401() throws Exception {
        when(authService.login(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@example.com\",\"password\":\"wrongpass1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void disabledAccountReturns401() throws Exception {
        when(authService.login(any())).thenThrow(new DisabledException("disabled"));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@example.com\",\"password\":\"wrongpass1\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{bad"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void validationErrorsStillReturn400WithFieldErrors() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists());
    }

    @Test
    void missingRequestParamReturns400() throws Exception {
        mvc.perform(post("/api/cart/add").header("Authorization", userToken()).param("productId", "1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void wrongParamTypeReturns400() throws Exception {
        mvc.perform(post("/api/cart/add").header("Authorization", userToken())
                        .param("productId", "abc").param("quantity", "1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingTokenReturns401() throws Exception {
        mvc.perform(get("/api/cart")).andExpect(status().isUnauthorized());
    }

    @Test
    void malformedTokenReturns401() throws Exception {
        mvc.perform(get("/api/cart").header("Authorization", "Bearer abc.def.ghi"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenReturns401() throws Exception {
        String expired = Jwts.builder()
                .subject(USER_EMAIL)
                .issuedAt(new Date(System.currentTimeMillis() - 120_000))
                .expiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        mvc.perform(get("/api/cart").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validTokenStillReachesProtectedEndpoint() throws Exception {
        when(shoppingCartService.getUserCart(USER_EMAIL)).thenReturn(List.of());

        mvc.perform(get("/api/cart").header("Authorization", userToken()))
                .andExpect(status().isOk());
    }

    @Test
    void userCallingAdminEndpointReturns403() throws Exception {
        mvc.perform(post("/api/products").header("Authorization", userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subcategoryId\":1,\"name\":\"n\",\"slug\":\"s\",\"price\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void constraintViolationReturns409() throws Exception {
        when(productService.getProductBySlug("dup")).thenThrow(new DataIntegrityViolationException("duplicate"));

        mvc.perform(get("/api/products/slug/dup"))
                .andExpect(status().isConflict());
    }

    // S4: catalog reads are public, admin listing is not

    @Test
    void catalogReadsArePublic() throws Exception {
        when(productService.getActiveProducts()).thenReturn(List.of());
        when(productService.searchProductsByName("kb")).thenReturn(List.of());

        mvc.perform(get("/api/products/active")).andExpect(status().isOk());
        mvc.perform(get("/api/products/search").param("keyword", "kb")).andExpect(status().isOk());
        mvc.perform(get("/api/products/1")).andExpect(status().isOk());
        mvc.perform(get("/api/products/slug/keyboard")).andExpect(status().isOk());
    }

    @Test
    void fullProductListingStillRequiresAdmin() throws Exception {
        mvc.perform(get("/api/products")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/products").header("Authorization", userToken())).andExpect(status().isForbidden());
    }
}
