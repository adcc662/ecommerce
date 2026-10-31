package com.example.ecommerce.security;

import com.example.ecommerce.exception.TooManyRequestsException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limits failed logins per email+IP and per email within a fixed window.
 * The per-email limit caps brute force even when the client rotates IPs.
 * State is in memory, so each application instance counts separately.
 */
@Component
public class LoginAttemptLimiter {

    static final int MAX_FAILURES_PER_EMAIL_AND_IP = 5;
    static final int MAX_FAILURES_PER_EMAIL = 20;
    static final Duration WINDOW = Duration.ofMinutes(15);
    private static final int CLEANUP_THRESHOLD = 10_000;

    private record Window(Instant start, int failures) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    @Autowired
    public LoginAttemptLimiter() {
        this(Clock.systemUTC());
    }

    LoginAttemptLimiter(Clock clock) {
        this.clock = clock;
    }

    public void checkAllowed(String email, String clientIp) {
        Instant now = clock.instant();
        long retryAfter = Math.max(
                retryAfter(emailAndIpKey(email, clientIp), MAX_FAILURES_PER_EMAIL_AND_IP, now),
                retryAfter(emailKey(email), MAX_FAILURES_PER_EMAIL, now));
        if (retryAfter > 0) {
            throw new TooManyRequestsException("Too many failed login attempts. Try again later.", retryAfter);
        }
    }

    public void recordFailure(String email, String clientIp) {
        Instant now = clock.instant();
        increment(emailAndIpKey(email, clientIp), now);
        increment(emailKey(email), now);
        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.values().removeIf(window -> isExpired(window, now));
        }
    }

    public void recordSuccess(String email, String clientIp) {
        windows.remove(emailAndIpKey(email, clientIp));
        windows.remove(emailKey(email));
    }

    private void increment(String key, Instant now) {
        windows.compute(key, (k, window) -> window == null || isExpired(window, now)
                ? new Window(now, 1)
                : new Window(window.start(), window.failures() + 1));
    }

    private long retryAfter(String key, int limit, Instant now) {
        Window window = windows.get(key);
        if (window == null || isExpired(window, now) || window.failures() < limit) {
            return 0;
        }
        return Math.max(1, Duration.between(now, window.start().plus(WINDOW)).toSeconds());
    }

    private static boolean isExpired(Window window, Instant now) {
        return !now.isBefore(window.start().plus(WINDOW));
    }

    private static String emailKey(String email) {
        return normalize(email);
    }

    private static String emailAndIpKey(String email, String clientIp) {
        return normalize(email) + "|" + clientIp;
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
