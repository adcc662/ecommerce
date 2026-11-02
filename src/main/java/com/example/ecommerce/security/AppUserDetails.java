package com.example.ecommerce.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

/** Spring Security user that also carries the user's current token version. */
@Getter
public class AppUserDetails extends User {

    private final int tokenVersion;

    public AppUserDetails(String username, String password, boolean active,
                          Collection<? extends GrantedAuthority> authorities, int tokenVersion) {
        super(username, password, active, true, true, active, authorities);
        this.tokenVersion = tokenVersion;
    }
}
