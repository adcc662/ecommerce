package com.example.ecommerce.service;

import com.example.ecommerce.models.dto.request.LoginRequest;
import com.example.ecommerce.models.dto.request.RegisterRequest;
import com.example.ecommerce.models.dto.response.AuthResponse;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
}
