package com.example.ecommerce.security;

import com.example.ecommerce.exception.TooManyRequestsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Limits failed logins within a fixed window, with three counters:
 * <ul>
 *   <li>per email+IP: one client guessing one account's password;</li>
 *   <li>per email: brute force on one account even when the client rotates IPs;</li>
 *   <li>per IP: password spraying, one client trying a common password against many accounts.</li>
 * </ul>
 * When the per-email or per-IP limit is reached, IPs that logged in successfully to that account before
 * ("known IPs") keep working for that account, so failures from elsewhere, or from others sharing the IP,
 * cannot lock the owner out of their usual device. The per email+IP limit still applies to known IPs.
 * Counters live in Redis, so every instance shares them and they survive restarts.
 * If Redis is unreachable the limiter fails open: logins keep working and a warning is logged.
 */
@Slf4j
@Component
public class LoginAttemptLimiter {

    static final int MAX_FAILURES_PER_EMAIL_AND_IP = 5;
    static final int MAX_FAILURES_PER_EMAIL = 20;
    static final int MAX_FAILURES_PER_IP = 50;
    static final Duration KNOWN_IP_TTL = Duration.ofDays(30);
    static final Duration WINDOW = Duration.ofMinutes(15);
    private static final String KEY_PREFIX = "login-fail:";

    /** Increments a counter and starts its window on the first failure, atomically. */
    private static final RedisScript<Long> INCREMENT = RedisScript.of("""
            local failures = redis.call('INCR', KEYS[1])
            if failures == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return failures
            """, Long.class);

    private final StringRedisTemplate redis;
    private final Duration window;
    private final String keyPrefix;

    @Autowired
    public LoginAttemptLimiter(StringRedisTemplate redis) {
        this(redis, WINDOW, KEY_PREFIX);
    }

    LoginAttemptLimiter(StringRedisTemplate redis, Duration window, String keyPrefix) {
        this.redis = redis;
        this.window = window;
        this.keyPrefix = keyPrefix;
    }

    public void checkAllowed(String email, String clientIp) {
        long retryAfter;
        try {
            retryAfter = retryAfter(emailAndIpKey(email, clientIp), MAX_FAILURES_PER_EMAIL_AND_IP);
            // The per-email and per-IP limits stop attacks from many IPs and on many accounts. An IP this
            // account logged in from before is exempt from both, so neither failures elsewhere nor a shared
            // IP (office, NAT, proxy) blocked for spraying can lock the owner out of their usual device.
            long sharedRetryAfter = Math.max(
                    retryAfter(emailKey(email), MAX_FAILURES_PER_EMAIL),
                    retryAfter(ipKey(clientIp), MAX_FAILURES_PER_IP));
            if (sharedRetryAfter > 0 && !isKnownIp(email, clientIp)) {
                retryAfter = Math.max(retryAfter, sharedRetryAfter);
            }
        } catch (DataAccessException ex) {
            log.warn("Login rate limiter unavailable, allowing attempt: {}", ex.getMessage());
            return;
        }
        if (retryAfter > 0) {
            throw new TooManyRequestsException("Too many failed login attempts. Try again later.", retryAfter);
        }
    }

    public void recordFailure(String email, String clientIp) {
        String ttl = Long.toString(window.toMillis());
        try {
            redis.execute(INCREMENT, List.of(emailAndIpKey(email, clientIp)), ttl);
            redis.execute(INCREMENT, List.of(emailKey(email)), ttl);
            redis.execute(INCREMENT, List.of(ipKey(clientIp)), ttl);
        } catch (DataAccessException ex) {
            log.warn("Login rate limiter unavailable, failure not counted: {}", ex.getMessage());
        }
    }

    /**
     * Clears the account's counters and remembers the IP as known for this account.
     * The per-IP counter is kept: logging in to one's own account must not reset a spraying count.
     */
    public void recordSuccess(String email, String clientIp) {
        try {
            redis.delete(List.of(emailAndIpKey(email, clientIp), emailKey(email)));
            String knownIps = knownIpsKey(email);
            redis.opsForSet().add(knownIps, String.valueOf(clientIp));
            redis.expire(knownIps, KNOWN_IP_TTL);
        } catch (DataAccessException ex) {
            log.warn("Login rate limiter unavailable, counters not cleared: {}", ex.getMessage());
        }
    }

    /**
     * Clears every counter for the email, from all IPs (e.g. after a password reset proves ownership).
     * Uses SCAN, not KEYS, so Redis is never blocked; this runs rarely.
     */
    public void clearFailures(String email) {
        String hash = hash(email);
        List<String> keys = new ArrayList<>();
        keys.add(emailKey(email));
        // A reset starts over: IPs that knew the old password lose their exemption
        keys.add(knownIpsKey(email));
        ScanOptions options = ScanOptions.scanOptions().match(keyPrefix + "email-ip:" + hash + ":*").count(1000).build();
        try {
            try (Cursor<String> cursor = redis.scan(options)) {
                cursor.forEachRemaining(keys::add);
            }
            redis.delete(keys);
        } catch (DataAccessException ex) {
            log.warn("Login rate limiter unavailable, counters not cleared: {}", ex.getMessage());
        }
    }

    /** Seconds until the window ends when the limit is reached, otherwise 0. */
    private long retryAfter(String key, int limit) {
        String failures = redis.opsForValue().get(key);
        if (failures == null || Long.parseLong(failures) < limit) {
            return 0;
        }
        Long ttlMillis = redis.getExpire(key, TimeUnit.MILLISECONDS);
        if (ttlMillis == null || ttlMillis <= 0) {
            // The window ended between the two reads (or the TTL is unknown): ask to retry shortly
            return 1;
        }
        return Math.max(1, (ttlMillis + 999) / 1000);
    }

    private boolean isKnownIp(String email, String clientIp) {
        return Boolean.TRUE.equals(redis.opsForSet().isMember(knownIpsKey(email), String.valueOf(clientIp)));
    }

    private String ipKey(String clientIp) {
        return keyPrefix + "ip:" + clientIp;
    }

    private String knownIpsKey(String email) {
        return keyPrefix + "known-ips:" + hash(email);
    }

    private String emailKey(String email) {
        return keyPrefix + "email:" + hash(email);
    }

    private String emailAndIpKey(String email, String clientIp) {
        return keyPrefix + "email-ip:" + hash(email) + ":" + clientIp;
    }

    /** Keys carry a hash, not the address itself, so Redis holds no plain emails. */
    private static String hash(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
