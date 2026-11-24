package com.example.ecommerce.security;

import com.example.ecommerce.exception.TooManyRequestsException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Runs against the Redis on REDIS_HOST:REDIS_PORT (default localhost:6379), like the app itself. */
class LoginAttemptLimiterTest {

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redis;

    private final String prefix = "test:login-fail:" + UUID.randomUUID() + ":";
    private final LoginAttemptLimiter limiter = new LoginAttemptLimiter(redis, LoginAttemptLimiter.WINDOW, prefix);

    @BeforeAll
    static void connect() {
        connectionFactory = factory(System.getenv().getOrDefault("REDIS_HOST", "localhost"),
                Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379")));
        redis = new StringRedisTemplate(connectionFactory);
    }

    @AfterAll
    static void disconnect() {
        connectionFactory.destroy();
    }

    @AfterEach
    void cleanUp() {
        Set<String> keys = redis.keys(prefix + "*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    private static LettuceConnectionFactory factory(String host, int port) {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(host, port),
                LettuceClientConfiguration.builder().commandTimeout(Duration.ofMillis(500)).build());
        factory.afterPropertiesSet();
        factory.start();
        return factory;
    }

    private void fail(LoginAttemptLimiter target, String email, String ip, int times) {
        for (int i = 0; i < times; i++) {
            target.recordFailure(email, ip);
        }
    }

    @Test
    void blocksAfterFiveFailuresFromSameIp() {
        fail(limiter, "a@example.com", "1.1.1.1", 4);
        assertThatCode(() -> limiter.checkAllowed("a@example.com", "1.1.1.1")).doesNotThrowAnyException();

        limiter.recordFailure("a@example.com", "1.1.1.1");

        assertThatThrownBy(() -> limiter.checkAllowed("a@example.com", "1.1.1.1"))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessage("Too many failed login attempts. Try again later.")
                .satisfies(ex -> assertThat(((TooManyRequestsException) ex).getRetryAfterSeconds())
                        .isBetween(1L, LoginAttemptLimiter.WINDOW.toSeconds()));
    }

    @Test
    void emailMatchingIsCaseInsensitive() {
        fail(limiter, "A@Example.com", "1.1.1.1", 5);

        assertThatThrownBy(() -> limiter.checkAllowed(" a@example.com ", "1.1.1.1"))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void otherIpsKeepTryingUntilThePerEmailLimit() {
        fail(limiter, "a@example.com", "1.1.1.1", 5);
        assertThatCode(() -> limiter.checkAllowed("a@example.com", "2.2.2.2")).doesNotThrowAnyException();

        for (int i = 0; i < 15; i++) {
            limiter.recordFailure("a@example.com", "10.0.0." + i);
        }
        assertThatThrownBy(() -> limiter.checkAllowed("a@example.com", "9.9.9.9"))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void windowExpires() throws InterruptedException {
        LoginAttemptLimiter shortWindow = new LoginAttemptLimiter(redis, Duration.ofMillis(300), prefix);
        fail(shortWindow, "a@example.com", "1.1.1.1", 5);
        assertThatThrownBy(() -> shortWindow.checkAllowed("a@example.com", "1.1.1.1"))
                .isInstanceOf(TooManyRequestsException.class);

        Thread.sleep(500);

        assertThatCode(() -> shortWindow.checkAllowed("a@example.com", "1.1.1.1")).doesNotThrowAnyException();
    }

    @Test
    void successResetsTheCounters() {
        fail(limiter, "a@example.com", "1.1.1.1", 4);
        limiter.recordSuccess("a@example.com", "1.1.1.1");
        fail(limiter, "a@example.com", "1.1.1.1", 4);

        assertThatCode(() -> limiter.checkAllowed("a@example.com", "1.1.1.1")).doesNotThrowAnyException();
    }

    // S1: counts are shared and survive a restart

    @Test
    void countsAreSharedBetweenInstances() {
        LoginAttemptLimiter otherInstance = new LoginAttemptLimiter(redis, LoginAttemptLimiter.WINDOW, prefix);
        fail(limiter, "a@example.com", "1.1.1.1", 3);
        fail(otherInstance, "a@example.com", "1.1.1.1", 2);

        assertThatThrownBy(() -> new LoginAttemptLimiter(redis, LoginAttemptLimiter.WINDOW, prefix)
                .checkAllowed("a@example.com", "1.1.1.1"))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void everyCounterExpiresAndStoresNoPlainEmail() {
        limiter.recordFailure("a@example.com", "1.1.1.1");

        Set<String> keys = redis.keys(prefix + "*");
        // per email+IP, per email and per IP
        assertThat(keys).hasSize(3).noneMatch(key -> key.contains("a@example.com"));
        for (String key : keys) {
            assertThat(redis.getExpire(key)).isBetween(1L, LoginAttemptLimiter.WINDOW.toSeconds());
        }
    }

    @Test
    void unreachableRedisFailsOpen() {
        LettuceConnectionFactory dead = factory("127.0.0.1", 1);
        try {
            LoginAttemptLimiter offline = new LoginAttemptLimiter(new StringRedisTemplate(dead),
                    LoginAttemptLimiter.WINDOW, prefix);

            assertThatCode(() -> {
                fail(offline, "a@example.com", "1.1.1.1", 6);
                offline.checkAllowed("a@example.com", "1.1.1.1");
                offline.recordSuccess("a@example.com", "1.1.1.1");
            }).doesNotThrowAnyException();
        } finally {
            dead.destroy();
        }
    }

    @Test
    void clearFailuresUnblocksTheEmailFromEveryIp() {
        fail(limiter, "a@example.com", "1.1.1.1", LoginAttemptLimiter.MAX_FAILURES_PER_EMAIL_AND_IP);
        fail(limiter, "a@example.com", "2.2.2.2", LoginAttemptLimiter.MAX_FAILURES_PER_EMAIL_AND_IP);
        fail(limiter, "b@example.com", "1.1.1.1", LoginAttemptLimiter.MAX_FAILURES_PER_EMAIL_AND_IP);

        limiter.clearFailures("A@Example.com");

        assertThatCode(() -> limiter.checkAllowed("a@example.com", "1.1.1.1")).doesNotThrowAnyException();
        assertThatCode(() -> limiter.checkAllowed("a@example.com", "2.2.2.2")).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.checkAllowed("b@example.com", "1.1.1.1"))
                .isInstanceOf(TooManyRequestsException.class);
    }

    // Password spraying: one IP trying one password against many accounts

    @Test
    void oneIpIsBlockedAfterTooManyFailuresAcrossEmails() {
        for (int i = 0; i < LoginAttemptLimiter.MAX_FAILURES_PER_IP; i++) {
            limiter.recordFailure("user" + i + "@example.com", "1.1.1.1");
        }

        assertThatThrownBy(() -> limiter.checkAllowed("someone-new@example.com", "1.1.1.1"))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessage("Too many failed login attempts. Try again later.");
        assertThatCode(() -> limiter.checkAllowed("someone-new@example.com", "2.2.2.2")).doesNotThrowAnyException();
    }

    @Test
    void aSuccessfulLoginDoesNotResetThePerIpCounter() {
        for (int i = 0; i < LoginAttemptLimiter.MAX_FAILURES_PER_IP - 1; i++) {
            limiter.recordFailure("user" + i + "@example.com", "1.1.1.1");
        }
        limiter.recordSuccess("attacker-own-account@example.com", "1.1.1.1");
        limiter.recordFailure("one-more@example.com", "1.1.1.1");

        assertThatThrownBy(() -> limiter.checkAllowed("someone-new@example.com", "1.1.1.1"))
                .isInstanceOf(TooManyRequestsException.class);
    }

    // Lockout abuse: failures from other IPs must not lock the owner out of their usual device

    @Test
    void lockedAccountStillLogsInFromAnIpThatSucceededBefore() {
        limiter.recordSuccess("a@example.com", "1.1.1.1");
        for (int i = 0; i < LoginAttemptLimiter.MAX_FAILURES_PER_EMAIL; i++) {
            limiter.recordFailure("a@example.com", "10.0.0." + i);
        }

        assertThatCode(() -> limiter.checkAllowed("a@example.com", "1.1.1.1")).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.checkAllowed("a@example.com", "9.9.9.9"))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void knownIpIsStillLimitedByItsOwnFailures() {
        limiter.recordSuccess("a@example.com", "1.1.1.1");
        fail(limiter, "a@example.com", "1.1.1.1", LoginAttemptLimiter.MAX_FAILURES_PER_EMAIL_AND_IP);

        assertThatThrownBy(() -> limiter.checkAllowed("a@example.com", "1.1.1.1"))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void knownIpsAreRememberedPerEmailForAMonthWithoutThePlainEmail() {
        limiter.recordSuccess("a@example.com", "1.1.1.1");

        Set<String> keys = redis.keys(prefix + "*");
        assertThat(keys).hasSize(1).noneMatch(key -> key.contains("a@example.com"));
        assertThat(redis.getExpire(keys.iterator().next()))
                .isBetween(LoginAttemptLimiter.KNOWN_IP_TTL.toSeconds() - 5, LoginAttemptLimiter.KNOWN_IP_TTL.toSeconds());
    }

    @Test
    void sharedIpBlockedForSprayingStillLetsAccountsThatLoggedInFromItBefore() {
        limiter.recordSuccess("colleague@example.com", "1.1.1.1");
        for (int i = 0; i < LoginAttemptLimiter.MAX_FAILURES_PER_IP; i++) {
            limiter.recordFailure("user" + i + "@example.com", "1.1.1.1");
        }

        assertThatCode(() -> limiter.checkAllowed("colleague@example.com", "1.1.1.1")).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.checkAllowed("victim@example.com", "1.1.1.1"))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void clearFailuresAlsoForgetsKnownIps() {
        limiter.recordSuccess("a@example.com", "1.1.1.1");
        limiter.clearFailures("a@example.com");
        for (int i = 0; i < LoginAttemptLimiter.MAX_FAILURES_PER_EMAIL; i++) {
            limiter.recordFailure("a@example.com", "10.0.0." + i);
        }

        assertThatThrownBy(() -> limiter.checkAllowed("a@example.com", "1.1.1.1"))
                .isInstanceOf(TooManyRequestsException.class);
    }
}
