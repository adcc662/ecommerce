package com.example.ecommerce.controller;

import com.example.ecommerce.config.SecurityConfig;
import com.example.ecommerce.exception.ConflictException;
import com.example.ecommerce.models.dto.response.OrderResponse;
import com.example.ecommerce.models.enums.OrderStatus;
import com.example.ecommerce.security.JwtAuthenticationFilter;
import com.example.ecommerce.security.JwtUtil;
import com.example.ecommerce.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OrderController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtUtil.class})
@TestPropertySource(properties = {
        "jwt.secret=test-only-secret-0123456789abcdef0123456789abcdef",
        "jwt.expiration=60000",
        "app.cors.allowed-origins=http://localhost:3000"
})
class OrderCancelEndpointTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtUtil jwtUtil;

    @MockitoBean
    private OrderService orderService;
    @MockitoBean
    private UserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        when(userDetailsService.loadUserByUsername("user@example.com")).thenReturn(
                User.withUsername("user@example.com").password("x").roles("USER").build());
    }

    private String token() {
        return "Bearer " + jwtUtil.generateToken("user@example.com");
    }

    @Test
    void cancelRequiresAuthentication() throws Exception {
        mvc.perform(post("/api/orders/1/cancel")).andExpect(status().isUnauthorized());
        verify(orderService, never()).cancelMyOrder(any(), any());
    }

    @Test
    void customerCancelsTheirOrder() throws Exception {
        when(orderService.cancelMyOrder(1L, "user@example.com"))
                .thenReturn(OrderResponse.builder().id(1L).orderStatus(OrderStatus.CANCELLED).build());

        mvc.perform(post("/api/orders/1/cancel").header("Authorization", token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderStatus").value("CANCELLED"));
    }

    @Test
    void cancellingAPaidOrderReturnsConflict() throws Exception {
        when(orderService.cancelMyOrder(1L, "user@example.com"))
                .thenThrow(new ConflictException("Only unpaid (PENDING) orders can be cancelled"));

        mvc.perform(post("/api/orders/1/cancel").header("Authorization", token()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Only unpaid (PENDING) orders can be cancelled"));
    }
}
