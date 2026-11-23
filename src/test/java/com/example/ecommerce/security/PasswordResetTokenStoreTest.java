package com.example.ecommerce.security;

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

/** Runs against the Redis on REDIS_HOST:REDIS_PORT (default localhost:6379), like the app itself. */
class PasswordResetTokenStoreTest {

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redis;

    private final String prefix = "test:pwd-reset:" + UUID.randomUUID() + ":";
    private final PasswordResetTokenStore store =
            new PasswordResetTokenStore(redis, PasswordResetTokenStore.TOKEN_TTL, prefix);

    @BeforeAll
    static void connect() {
        connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(System.getenv().getOrDefault("REDIS_HOST", "localhost"),
                        Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"))),
                LettuceClientConfiguration.builder().commandTimeout(Duration.ofMillis(500)).build());
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
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

    @Test
    void issuedTokenResolvesToItsUser() {
        String token = store.issue(42L);

        assertThat(token).hasSizeGreaterThanOrEqualTo(43);
        assertThat(store.consume(token)).contains(42L);
    }

    @Test
    void tokenWorksOnlyOnce() {
        String token = store.issue(42L);
        store.consume(token);

        assertThat(store.consume(token)).isEmpty();
    }

    @Test
    void newTokenInvalidatesThePreviousOne() {
        String first = store.issue(42L);
        String second = store.issue(42L);

        assertThat(store.consume(first)).isEmpty();
        assertThat(store.consume(second)).contains(42L);
    }

    @Test
    void unknownTokenIsRejected() {
        assertThat(store.consume("not-a-real-token")).isEmpty();
        assertThat(store.consume(null)).isEmpty();
    }

    @Test
    void expiredTokenIsRejected() throws InterruptedException {
        PasswordResetTokenStore shortLived = new PasswordResetTokenStore(redis, Duration.ofMillis(50), prefix);
        String token = shortLived.issue(42L);

        Thread.sleep(150);

        assertThat(shortLived.consume(token)).isEmpty();
    }

    @Test
    void redisHoldsNoPlainToken() {
        String token = store.issue(42L);

        assertThat(redis.keys(prefix + "*")).noneMatch(key -> key.contains(token));
    }

    @Test
    void requestsArePerEmailLimitedCaseInsensitively() {
        for (int i = 0; i < PasswordResetTokenStore.MAX_REQUESTS_PER_EMAIL; i++) {
            assertThat(store.allowRequest("a@example.com")).isTrue();
        }

        assertThat(store.allowRequest("A@Example.com")).isFalse();
        assertThat(store.allowRequest("b@example.com")).isTrue();
    }
}
