package com.example.ecommerce.security;

import com.example.ecommerce.exception.TooManyRequestsException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginAttemptLimiterTest {

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-31T12:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MutableClock clock = new MutableClock();
    private final LoginAttemptLimiter limiter = new LoginAttemptLimiter(clock);

    private void fail(String email, String ip, int times) {
        for (int i = 0; i < times; i++) {
            limiter.recordFailure(email, ip);
        }
    }

    @Test
    void blocksAfterFiveFailuresFromSameIp() {
        fail("a@example.com", "1.1.1.1", 5);

        assertThatThrownBy(() -> limiter.checkAllowed("a@example.com", "1.1.1.1"))
                .isInstanceOf(TooManyRequestsException.class)
                .satisfies(ex -> assertThat(((TooManyRequestsException) ex).getRetryAfterSeconds()).isPositive());
    }

    @Test
    void emailMatchingIsCaseInsensitive() {
        fail("A@Example.com", "1.1.1.1", 5);

        assertThatThrownBy(() -> limiter.checkAllowed("a@example.com", "1.1.1.1"))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void otherIpsKeepTryingUntilThePerEmailLimit() {
        fail("a@example.com", "1.1.1.1", 5);
        assertThatCode(() -> limiter.checkAllowed("a@example.com", "2.2.2.2")).doesNotThrowAnyException();

        for (int i = 0; i < 15; i++) {
            limiter.recordFailure("a@example.com", "10.0.0." + i);
        }
        assertThatThrownBy(() -> limiter.checkAllowed("a@example.com", "9.9.9.9"))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void windowExpires() {
        fail("a@example.com", "1.1.1.1", 5);
        clock.advance(Duration.ofMinutes(16));

        assertThatCode(() -> limiter.checkAllowed("a@example.com", "1.1.1.1")).doesNotThrowAnyException();
    }

    @Test
    void successResetsTheCounters() {
        fail("a@example.com", "1.1.1.1", 4);
        limiter.recordSuccess("a@example.com", "1.1.1.1");
        fail("a@example.com", "1.1.1.1", 4);

        assertThatCode(() -> limiter.checkAllowed("a@example.com", "1.1.1.1")).doesNotThrowAnyException();
    }
}
