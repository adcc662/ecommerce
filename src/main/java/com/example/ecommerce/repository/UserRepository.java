package com.example.ecommerce.repository;

import com.example.ecommerce.models.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    /** Atomic increment, so concurrent "log out everywhere" calls never lose an update. */
    @Modifying
    @Query("UPDATE User u SET u.tokenVersion = u.tokenVersion + 1 WHERE u.email = :email")
    int incrementTokenVersion(@Param("email") String email);

    /**
     * Sets the password and revokes every issued token in one atomic statement, so a concurrent
     * "log out everywhere" can never lose its increment. Returns 0 when the user is gone or inactive.
     */
    @Modifying
    @Query("UPDATE User u SET u.passwordHash = :passwordHash, u.tokenVersion = u.tokenVersion + 1 "
            + "WHERE u.id = :id AND u.isActive = true")
    int updatePasswordAndRevokeTokens(@Param("id") Long id, @Param("passwordHash") String passwordHash);
}
