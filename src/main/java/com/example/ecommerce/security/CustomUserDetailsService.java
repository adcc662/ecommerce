package com.example.ecommerce.security;

import com.example.ecommerce.models.entity.User;
import com.example.ecommerce.models.enums.UserRole;
import com.example.ecommerce.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

        UserRole role = user.getRole() != null ? user.getRole() : UserRole.USER;

        boolean active = Boolean.TRUE.equals(user.getIsActive());
        int tokenVersion = user.getTokenVersion() != null ? user.getTokenVersion() : 0;

        return new AppUserDetails(
                user.getEmail(),
                user.getPasswordHash(),
                active,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())),
                tokenVersion);
    }
}
