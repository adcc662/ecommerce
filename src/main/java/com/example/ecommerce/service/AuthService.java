package com.example.ecommerce.service;

import com.example.ecommerce.models.dto.request.LoginRequest;
import com.example.ecommerce.models.dto.request.RegisterRequest;
import com.example.ecommerce.models.dto.response.AuthResponse;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request, String clientIp);

    void logoutEverywhere(String email);

    /** Emails a reset link when the email belongs to an active user; does nothing visible otherwise. */
    void requestPasswordReset(String email);

    /** Sets a new password using a reset token and revokes every token issued before. */
    void resetPassword(String token, String newPassword);
}
