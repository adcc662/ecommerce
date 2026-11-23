package com.example.ecommerce.controller;

import com.example.ecommerce.config.SecurityConfig;
import com.example.ecommerce.exception.InvalidTokenException;
import com.example.ecommerce.security.JwtAuthenticationFilter;
import com.example.ecommerce.security.JwtUtil;
import com.example.ecommerce.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtUtil.class})
@TestPropertySource(properties = {
        "jwt.secret=test-only-secret-0123456789abcdef0123456789abcdef",
        "jwt.expiration=60000",
        "app.cors.allowed-origins=http://localhost:3000"
})
class PasswordResetEndpointsTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService authService;
    @MockitoBean
    private UserDetailsService userDetailsService;

    private org.springframework.test.web.servlet.ResultActions postJson(String path, String json) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void forgotPasswordIsPublicAndAlwaysAccepted() throws Exception {
        postJson("/api/auth/forgot-password", "{\"email\":\"a@example.com\"}").andExpect(status().isAccepted());
        verify(authService).requestPasswordReset("a@example.com");
    }

    @Test
    void forgotPasswordRejectsAnInvalidEmail() throws Exception {
        postJson("/api/auth/forgot-password", "{\"email\":\"not-an-email\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").value("Email should be valid"));
        verify(authService, never()).requestPasswordReset(any());
    }

    @Test
    void resetPasswordReturnsNoContent() throws Exception {
        postJson("/api/auth/reset-password", "{\"token\":\"t\",\"newPassword\":\"new-password\"}")
                .andExpect(status().isNoContent());
        verify(authService).resetPassword("t", "new-password");
    }

    @Test
    void resetPasswordRejectsAShortPassword() throws Exception {
        postJson("/api/auth/reset-password", "{\"token\":\"t\",\"newPassword\":\"short\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.newPassword").value("Password must be at least 8 characters"));
        verify(authService, never()).resetPassword(any(), any());
    }

    @Test
    void resetPasswordWithABadTokenReturnsBadRequest() throws Exception {
        doThrow(new InvalidTokenException("Invalid or expired reset token"))
                .when(authService).resetPassword("bad", "new-password");

        postJson("/api/auth/reset-password", "{\"token\":\"bad\",\"newPassword\":\"new-password\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid or expired reset token"));
    }
}
