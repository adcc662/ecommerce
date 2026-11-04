package com.example.ecommerce.bootstrap;

import com.example.ecommerce.models.entity.User;
import com.example.ecommerce.models.enums.UserRole;
import com.example.ecommerce.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminBootstrapTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

    private AdminBootstrap bootstrap(String email, String password) {
        return new AdminBootstrap(userRepository, passwordEncoder, email, password);
    }

    @Test
    void doesNothingWhenNotConfigured() {
        bootstrap("", "").run(null);

        verifyNoInteractions(userRepository);
    }

    @Test
    void createsAdminWhenEmailIsFree() {
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("long-enough-password")).thenReturn("hash");

        bootstrap(" Admin@Example.com ", "long-enough-password").run(null);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("admin@example.com");
        assertThat(saved.getValue().getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("hash");
    }

    @Test
    void neverPromotesAnExistingAccount() {
        User existing = User.builder().email("admin@example.com").role(UserRole.USER).build();
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(existing));

        bootstrap("admin@example.com", "long-enough-password").run(null);

        assertThat(existing.getRole()).isEqualTo(UserRole.USER);
        verify(userRepository, never()).save(any());
    }

    @Test
    void rejectsShortPassword() {
        assertThatThrownBy(() -> bootstrap("admin@example.com", "short").run(null))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(userRepository);
    }

    @Test
    void emailIsLowercasedIndependentlyOfDefaultLocale() {
        java.util.Locale original = java.util.Locale.getDefault();
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"));
        try {
            when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.empty());
            when(passwordEncoder.encode("long-enough-password")).thenReturn("hash");

            bootstrap(" ADMIN@EXAMPLE.COM ", "long-enough-password").run(null);

            verify(userRepository).findByEmail("admin@example.com");
        } finally {
            java.util.Locale.setDefault(original);
        }
    }
}
