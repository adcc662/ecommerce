package com.example.ecommerce.bootstrap;

import com.example.ecommerce.models.entity.User;
import com.example.ecommerce.models.enums.UserRole;
import com.example.ecommerce.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first admin from ADMIN_EMAIL / ADMIN_PASSWORD when both are set.
 * An existing account with that email is never promoted: someone could have
 * registered it first to obtain admin rights.
 */
@Slf4j
@Component
public class AdminBootstrap implements ApplicationRunner {

    static final int MIN_PASSWORD_LENGTH = 12;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    public AdminBootstrap(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          @Value("${app.admin.email:}") String email,
                          @Value("${app.admin.password:}") String password) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.email = email == null ? "" : email.trim().toLowerCase();
        this.password = password == null ? "" : password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email.isEmpty() || password.isEmpty()) {
            return;
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException("ADMIN_PASSWORD must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }

        userRepository.findByEmail(email).ifPresentOrElse(existing -> {
            if (existing.getRole() != UserRole.ADMIN) {
                log.warn("ADMIN_EMAIL belongs to an existing non-admin account; it was not promoted");
            }
        }, () -> {
            userRepository.save(User.builder()
                    .email(email)
                    .passwordHash(passwordEncoder.encode(password))
                    .firstName("Admin")
                    .lastName("Account")
                    .isActive(true)
                    .role(UserRole.ADMIN)
                    .build());
            log.info("Created initial admin account");
        });
    }
}
