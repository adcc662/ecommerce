package com.example.ecommerce.service.impl;

import com.example.ecommerce.exception.TooManyRequestsException;
import com.example.ecommerce.models.dto.request.LoginRequest;
import com.example.ecommerce.models.entity.User;
import com.example.ecommerce.repository.UserRepository;
import com.example.ecommerce.security.JwtUtil;
import com.example.ecommerce.security.LoginAttemptLimiter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private LoginAttemptLimiter loginAttemptLimiter;

    @InjectMocks
    private AuthServiceImpl authService;

    private static final LoginRequest REQUEST = LoginRequest.builder().email("a@example.com").password("pw").build();

    @Test
    void failedLoginIsRecorded() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        assertThatThrownBy(() -> authService.login(REQUEST, "1.1.1.1")).isInstanceOf(BadCredentialsException.class);
        verify(loginAttemptLimiter).recordFailure("a@example.com", "1.1.1.1");
        verify(loginAttemptLimiter, never()).recordSuccess(any(), any());
    }

    @Test
    void blockedClientIsRejectedBeforeCheckingThePassword() {
        doThrow(new TooManyRequestsException("Too many", 60)).when(loginAttemptLimiter).checkAllowed("a@example.com", "1.1.1.1");

        assertThatThrownBy(() -> authService.login(REQUEST, "1.1.1.1")).isInstanceOf(TooManyRequestsException.class);
        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    void successfulLoginResetsCountersAndReturnsToken() {
        when(userRepository.findByEmail("a@example.com")).thenReturn(Optional.of(User.builder().email("a@example.com").build()));
        when(jwtUtil.generateToken("a@example.com")).thenReturn("token");

        assertThat(authService.login(REQUEST, "1.1.1.1").getToken()).isEqualTo("token");
        verify(loginAttemptLimiter).recordSuccess("a@example.com", "1.1.1.1");
    }
}
