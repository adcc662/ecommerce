package com.example.ecommerce.service.impl;

import com.example.ecommerce.exception.InvalidTokenException;
import com.example.ecommerce.models.entity.User;
import com.example.ecommerce.repository.UserRepository;
import com.example.ecommerce.security.JwtUtil;
import com.example.ecommerce.security.LoginAttemptLimiter;
import com.example.ecommerce.security.PasswordResetTokenStore;
import com.example.ecommerce.service.EmailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetTest {

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
    @Mock
    private PasswordResetTokenStore passwordResetTokenStore;
    @Mock
    private EmailService emailService;

    @InjectMocks
    private AuthServiceImpl authService;

    private static User user(boolean active) {
        return User.builder().id(7L).email("a@example.com").passwordHash("old-hash").isActive(active).tokenVersion(2).build();
    }

    @Test
    void activeUserReceivesAResetEmail() {
        when(passwordResetTokenStore.allowRequest("a@example.com")).thenReturn(true);
        when(userRepository.findByEmail("a@example.com")).thenReturn(Optional.of(user(true)));
        when(passwordResetTokenStore.issue(7L)).thenReturn("reset-token");

        authService.requestPasswordReset("a@example.com");

        verify(emailService).sendPasswordResetEmail("a@example.com", "reset-token");
    }

    @Test
    void unknownEmailIsIgnoredSilently() {
        when(passwordResetTokenStore.allowRequest("ghost@example.com")).thenReturn(true);
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        authService.requestPasswordReset("ghost@example.com");

        verify(passwordResetTokenStore, never()).issue(anyLong());
        verify(emailService, never()).sendPasswordResetEmail(any(), any());
    }

    @Test
    void inactiveUserGetsNoEmail() {
        when(passwordResetTokenStore.allowRequest("a@example.com")).thenReturn(true);
        when(userRepository.findByEmail("a@example.com")).thenReturn(Optional.of(user(false)));

        authService.requestPasswordReset("a@example.com");

        verify(emailService, never()).sendPasswordResetEmail(any(), any());
    }

    @Test
    void tooManyRequestsAreIgnoredSilently() {
        when(passwordResetTokenStore.allowRequest("a@example.com")).thenReturn(false);

        authService.requestPasswordReset("a@example.com");

        verify(userRepository, never()).findByEmail(any());
        verify(emailService, never()).sendPasswordResetEmail(any(), any());
    }

    @Test
    void validTokenSetsTheNewPasswordAndRevokesExistingSessionsAtomically() {
        when(passwordResetTokenStore.consume("reset-token")).thenReturn(Optional.of(7L));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user(true)));
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");
        when(userRepository.updatePasswordAndRevokeTokens(7L, "new-hash")).thenReturn(1);

        authService.resetPassword("reset-token", "new-password");

        verify(userRepository).updatePasswordAndRevokeTokens(7L, "new-hash");
        verify(userRepository, never()).save(any());
        verify(loginAttemptLimiter).clearFailures("a@example.com");
    }

    @Test
    void userDeactivatedDuringTheResetIsRejected() {
        when(passwordResetTokenStore.consume("reset-token")).thenReturn(Optional.of(7L));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user(true)));
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");
        when(userRepository.updatePasswordAndRevokeTokens(7L, "new-hash")).thenReturn(0);

        assertThatThrownBy(() -> authService.resetPassword("reset-token", "new-password"))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessage("Invalid or expired reset token");
        verify(loginAttemptLimiter, never()).clearFailures(any());
    }

    @Test
    void invalidTokenIsRejected() {
        when(passwordResetTokenStore.consume("bad")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.resetPassword("bad", "new-password"))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessage("Invalid or expired reset token");
        verify(userRepository, never()).updatePasswordAndRevokeTokens(anyLong(), any());
        verify(loginAttemptLimiter, never()).clearFailures(any());
    }

    @Test
    void tokenOfADeactivatedUserIsRejected() {
        when(passwordResetTokenStore.consume("reset-token")).thenReturn(Optional.of(7L));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user(false)));

        assertThatThrownBy(() -> authService.resetPassword("reset-token", "new-password"))
                .isInstanceOf(InvalidTokenException.class);
        verify(userRepository, never()).updatePasswordAndRevokeTokens(anyLong(), any());
    }
}
