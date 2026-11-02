package com.example.ecommerce.controller;

import com.example.ecommerce.config.SecurityConfig;
import com.example.ecommerce.security.AppUserDetails;
import com.example.ecommerce.security.JwtAuthenticationFilter;
import com.example.ecommerce.security.JwtUtil;
import com.example.ecommerce.service.AuthService;
import com.example.ecommerce.service.ShoppingCartService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AuthController.class, ShoppingCartController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtUtil.class})
@TestPropertySource(properties = {
        "jwt.secret=test-only-secret-0123456789abcdef0123456789abcdef",
        "jwt.expiration=60000",
        "app.cors.allowed-origins=http://localhost:3000"
})
class TokenRevocationTest {

    private static final String EMAIL = "user@example.com";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtUtil jwtUtil;

    @MockitoBean
    private AuthService authService;
    @MockitoBean
    private ShoppingCartService shoppingCartService;
    @MockitoBean
    private UserDetailsService userDetailsService;

    private void currentVersionIs(int version) {
        when(userDetailsService.loadUserByUsername(EMAIL)).thenReturn(new AppUserDetails(
                EMAIL, "x", true, List.of(new SimpleGrantedAuthority("ROLE_USER")), version));
        when(shoppingCartService.getUserCart(EMAIL)).thenReturn(List.of());
    }

    @Test
    void tokenCarriesTheUsersVersion() {
        assertThat(jwtUtil.extractTokenVersion(jwtUtil.generateToken(EMAIL, 3))).isEqualTo(3);
    }

    @Test
    void tokenFromCurrentVersionIsAccepted() throws Exception {
        currentVersionIs(2);
        mvc.perform(get("/api/cart").header("Authorization", "Bearer " + jwtUtil.generateToken(EMAIL, 2)))
                .andExpect(status().isOk());
    }

    @Test
    void tokenFromOlderVersionIsRejected() throws Exception {
        currentVersionIs(2);
        mvc.perform(get("/api/cart").header("Authorization", "Bearer " + jwtUtil.generateToken(EMAIL, 1)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokensIssuedBeforeVersioningCountAsVersionZero() throws Exception {
        currentVersionIs(0);
        String legacy = io.jsonwebtoken.Jwts.builder().subject(EMAIL)
                .issuedAt(new java.util.Date())
                .expiration(new java.util.Date(System.currentTimeMillis() + 60_000))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        "test-only-secret-0123456789abcdef0123456789abcdef".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .compact();
        mvc.perform(get("/api/cart").header("Authorization", "Bearer " + legacy)).andExpect(status().isOk());
    }

    @Test
    void plainUserDetailsStillWork() throws Exception {
        when(userDetailsService.loadUserByUsername(EMAIL))
                .thenReturn(User.withUsername(EMAIL).password("x").roles("USER").build());
        mvc.perform(get("/api/cart").header("Authorization", "Bearer " + jwtUtil.generateToken(EMAIL)))
                .andExpect(status().isOk());
    }

    @Test
    void logoutAllRequiresAuthentication() throws Exception {
        mvc.perform(post("/api/auth/logout-all")).andExpect(status().isUnauthorized());
        verify(authService, never()).logoutEverywhere(any());
    }

    @Test
    void logoutAllRevokesTheCallersTokens() throws Exception {
        currentVersionIs(0);
        mvc.perform(post("/api/auth/logout-all").header("Authorization", "Bearer " + jwtUtil.generateToken(EMAIL, 0)))
                .andExpect(status().isNoContent());
        verify(authService).logoutEverywhere(EMAIL);
    }
}
