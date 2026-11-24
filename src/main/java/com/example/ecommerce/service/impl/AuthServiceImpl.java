package com.example.ecommerce.service.impl;

import com.example.ecommerce.exception.EmailAlreadyExistsException;
import com.example.ecommerce.exception.InvalidTokenException;
import com.example.ecommerce.exception.ResourceNotFoundException;
import com.example.ecommerce.models.dto.request.LoginRequest;
import com.example.ecommerce.models.dto.request.RegisterRequest;
import com.example.ecommerce.models.dto.response.AuthResponse;
import com.example.ecommerce.models.entity.User;
import com.example.ecommerce.models.enums.UserRole;
import com.example.ecommerce.repository.UserRepository;
import com.example.ecommerce.security.JwtUtil;
import com.example.ecommerce.security.LoginAttemptLimiter;
import com.example.ecommerce.security.PasswordResetTokenStore;
import com.example.ecommerce.service.AuthService;
import com.example.ecommerce.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final LoginAttemptLimiter loginAttemptLimiter;
    private final PasswordResetTokenStore passwordResetTokenStore;
    private final EmailService emailService;

    @Override
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException("Email already exists");
        }

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phone(request.getPhone())
                .isActive(true)
                .role(UserRole.USER)
                .build();

        userRepository.save(user);

        String token = jwtUtil.generateToken(user.getEmail(), user.getTokenVersion());

        return AuthResponse.builder()
                .token(token)
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .build();
    }

    @Override
    @Transactional
    public void logoutEverywhere(String email) {
        if (userRepository.incrementTokenVersion(email) == 0) {
            throw new ResourceNotFoundException("User not found");
        }
    }

    @Override
    public AuthResponse login(LoginRequest request, String clientIp) {
        loginAttemptLimiter.checkAllowed(request.getEmail(), clientIp);
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );
        } catch (AuthenticationException ex) {
            loginAttemptLimiter.recordFailure(request.getEmail(), clientIp);
            throw ex;
        }
        loginAttemptLimiter.recordSuccess(request.getEmail(), clientIp);

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String token = jwtUtil.generateToken(user.getEmail(), user.getTokenVersion());

        return AuthResponse.builder()
                .token(token)
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .build();
    }

    @Override
    public void requestPasswordReset(String email) {
        // Limit first, for every email, so the endpoint behaves the same whether or not the account exists
        if (!passwordResetTokenStore.allowRequest(email)) {
            log.info("Password reset request limit reached, request ignored");
            return;
        }
        userRepository.findByEmail(email)
                .filter(user -> Boolean.TRUE.equals(user.getIsActive()))
                .ifPresent(user -> emailService.sendPasswordResetEmail(
                        user.getEmail(), passwordResetTokenStore.issue(user.getId())));
    }

    @Override
    @Transactional
    public void resetPassword(String token, String newPassword) {
        User user = passwordResetTokenStore.consume(token)
                .flatMap(userRepository::findById)
                .filter(found -> Boolean.TRUE.equals(found.getIsActive()))
                .orElseThrow(() -> new InvalidTokenException("Invalid or expired reset token"));

        // Single UPDATE: also catches a user deactivated after the lookup above
        if (userRepository.updatePasswordAndRevokeTokens(user.getId(), passwordEncoder.encode(newPassword)) == 0) {
            throw new InvalidTokenException("Invalid or expired reset token");
        }
        // Owning the email is proof enough: lift any lockout caused by failed logins
        loginAttemptLimiter.clearFailures(user.getEmail());
    }
}
