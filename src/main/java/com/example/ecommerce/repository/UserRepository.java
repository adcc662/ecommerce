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

    /**
     * Locks the user's row until the transaction ends, so one customer's checkouts run one at a time
     * and the unpaid-order cap cannot be bypassed with parallel requests.
     * NO KEY UPDATE still serialises checkouts but, unlike FOR UPDATE, does not block inserts that
     * reference the user (addresses, orders) or other sessions' foreign-key checks.
     */
    @Query(value = "SELECT 1 FROM users WHERE id = :id FOR NO KEY UPDATE", nativeQuery = true)
    Integer lockForCheckout(@Param("id") Long id);
    boolean existsByEmail(String email);

    /** Atomic increment, so concurrent "log out everywhere" calls never lose an update. */
    @Modifying
    @Query("UPDATE User u SET u.tokenVersion = u.tokenVersion + 1 WHERE u.email = :email")
    int incrementTokenVersion(@Param("email") String email);
}
