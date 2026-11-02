package com.example.ecommerce.controller;

import com.example.ecommerce.config.SecurityConfig;
import com.example.ecommerce.exception.ConflictException;
import com.example.ecommerce.security.JwtAuthenticationFilter;
import com.example.ecommerce.security.JwtUtil;
import com.example.ecommerce.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PaymentController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtUtil.class})
@TestPropertySource(properties = {
        "jwt.secret=test-only-secret-0123456789abcdef0123456789abcdef",
        "jwt.expiration=60000",
        "app.cors.allowed-origins=http://localhost:3000"
})
class PaymentSecurityTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtUtil jwtUtil;

    @MockitoBean
    private PaymentService paymentService;
    @MockitoBean
    private UserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        when(userDetailsService.loadUserByUsername("user@example.com")).thenReturn(
                User.withUsername("user@example.com").password("x").roles("USER").build());
        when(userDetailsService.loadUserByUsername("admin@example.com")).thenReturn(
                User.withUsername("admin@example.com").password("x").roles("ADMIN").build());
    }

    private String token(String email) {
        return "Bearer " + jwtUtil.generateToken(email);
    }

    @Test
    void payingRequiresLogin() throws Exception {
        mvc.perform(post("/api/orders/1/payments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethodId\":1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userCanPayAndListPayments() throws Exception {
        mvc.perform(post("/api/orders/1/payments").header("Authorization", token("user@example.com"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"paymentMethodId\":1}"))
                .andExpect(status().isCreated());
        when(paymentService.getPayments("user@example.com", 1L)).thenReturn(List.of());
        mvc.perform(get("/api/orders/1/payments").header("Authorization", token("user@example.com")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/payment-methods").header("Authorization", token("user@example.com")))
                .andExpect(status().isOk());
    }

    @Test
    void paymentMethodIdIsRequired() throws Exception {
        mvc.perform(post("/api/orders/1/payments").header("Authorization", token("user@example.com"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void conflictsReturn409() throws Exception {
        when(paymentService.createPayment(eq("user@example.com"), eq(1L), any()))
                .thenThrow(new ConflictException("Order already has an active payment"));
        mvc.perform(post("/api/orders/1/payments").header("Authorization", token("user@example.com"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"paymentMethodId\":1}"))
                .andExpect(status().isConflict());
    }

    @Test
    void onlyAdminsChangePaymentStatus() throws Exception {
        mvc.perform(patch("/api/payments/5/status").param("status", "COMPLETED")
                        .header("Authorization", token("user@example.com")))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/payments/5/status").param("status", "COMPLETED")
                        .header("Authorization", token("admin@example.com")))
                .andExpect(status().isOk());
    }
}
