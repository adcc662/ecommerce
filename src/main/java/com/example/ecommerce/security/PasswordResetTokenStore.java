package com.example.ecommerce.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Single-use password reset tokens kept in Redis with a TTL.
 * Only a SHA-256 hash of each token is stored, so a Redis dump cannot be used to reset passwords.
 * Issuing a new token for a user invalidates the previous one.
 * Unlike the login limiter this fails closed: if Redis is unreachable the request errors.
 */
@Component
public class PasswordResetTokenStore {

    static final Duration TOKEN_TTL = Duration.ofMinutes(30);
    static final int MAX_REQUESTS_PER_EMAIL = 3;
    static final Duration REQUEST_WINDOW = Duration.ofMinutes(15);
    private static final String KEY_PREFIX = "pwd-reset:";
    private static final int TOKEN_BYTES = 32;

    /** Stores the new token and drops the user's previous one, atomically. */
    private static final RedisScript<Long> ISSUE = RedisScript.of("""
            local previous = redis.call('GET', KEYS[2])
            if previous then
              redis.call('DEL', ARGV[3] .. previous)
            end
            redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2])
            redis.call('SET', KEYS[2], ARGV[4], 'PX', ARGV[2])
            return 1
            """, Long.class);

    /** Returns the token's user id and deletes the token, atomically, so it works only once. */
    private static final RedisScript<String> CONSUME = RedisScript.of("""
            local userId = redis.call('GET', KEYS[1])
            if not userId then
              return false
            end
            redis.call('DEL', KEYS[1])
            local userKey = ARGV[1] .. userId
            if redis.call('GET', userKey) == ARGV[2] then
              redis.call('DEL', userKey)
            end
            return userId
            """, String.class);

    private static final RedisScript<Long> INCREMENT = RedisScript.of("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """, Long.class);

    private final SecureRandom random = new SecureRandom();
    private final StringRedisTemplate redis;
    private final Duration tokenTtl;
    private final String keyPrefix;

    @Autowired
    public PasswordResetTokenStore(StringRedisTemplate redis) {
        this(redis, TOKEN_TTL, KEY_PREFIX);
    }

    PasswordResetTokenStore(StringRedisTemplate redis, Duration tokenTtl, String keyPrefix) {
        this.redis = redis;
        this.tokenTtl = tokenTtl;
        this.keyPrefix = keyPrefix;
    }

    /** Counts a reset request for this email; false once it exceeds the limit for the current window. */
    public boolean allowRequest(String email) {
        Long count = redis.execute(INCREMENT, List.of(keyPrefix + "requests:" + hash(normalize(email))),
                Long.toString(REQUEST_WINDOW.toMillis()));
        return count != null && count <= MAX_REQUESTS_PER_EMAIL;
    }

    /** Creates a new token for the user and returns it; the plain token is never stored. */
    public String issue(long userId) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String tokenHash = hash(token);
        redis.execute(ISSUE, List.of(tokenKeyPrefix() + tokenHash, userKeyPrefix() + userId),
                Long.toString(userId), Long.toString(tokenTtl.toMillis()), tokenKeyPrefix(), tokenHash);
        return token;
    }

    /** Returns the user id the token belongs to and invalidates it; empty when unknown, used or expired. */
    public Optional<Long> consume(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String tokenHash = hash(token);
        String userId = redis.execute(CONSUME, List.of(tokenKeyPrefix() + tokenHash), userKeyPrefix(), tokenHash);
        return Optional.ofNullable(userId).map(Long::valueOf);
    }

    private String tokenKeyPrefix() {
        return keyPrefix + "token:";
    }

    private String userKeyPrefix() {
        return keyPrefix + "user:";
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
