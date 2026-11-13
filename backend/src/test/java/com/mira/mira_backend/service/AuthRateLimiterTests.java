package com.mira.mira_backend.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthRateLimiterTests {

    private final MutableClock clock = new MutableClock(
            Instant.parse("2026-11-13T12:00:00Z")
    );

    private final AuthRateLimiter limiter = new AuthRateLimiter(
            5,
            30,
            5,
            60,
            Duration.ofMinutes(1),
            false,
            clock
    );

    @Test
    void loginEmailLimitIsEnforced() {
        for (int i = 0; i < 5; i++) {
            assertDoesNotThrow(() ->
                    limiter.checkLogin("user@example.com", "10.0.0.1"));
        }

        assertThrows(
                RateLimitExceededException.class,
                () -> limiter.checkLogin("USER@example.com ", "10.0.0.1")
        );
    }

    @Test
    void loginIpLimitIsEnforcedAcrossDifferentEmails() {
        for (int i = 0; i < 30; i++) {
            final int index = i;
            assertDoesNotThrow(() -> limiter.checkLogin(
                    "user" + index + "@example.com",
                    "10.0.0.1"
            ));
        }

        assertThrows(
                RateLimitExceededException.class,
                () -> limiter.checkLogin("new@example.com", "10.0.0.1")
        );
    }

    @Test
    void registerLimitIsEnforced() {
        for (int i = 0; i < 5; i++) {
            assertDoesNotThrow(() ->
                    limiter.checkRegister("10.0.0.2"));
        }

        assertThrows(
                RateLimitExceededException.class,
                () -> limiter.checkRegister("10.0.0.2")
        );
    }

    @Test
    void refreshLimitIsEnforced() {
        for (int i = 0; i < 60; i++) {
            assertDoesNotThrow(() ->
                    limiter.checkRefresh("10.0.0.3"));
        }

        assertThrows(
                RateLimitExceededException.class,
                () -> limiter.checkRefresh("10.0.0.3")
        );
    }

    @Test
    void windowsRefillAfterTheConfiguredDuration() {
        for (int i = 0; i < 5; i++) {
            limiter.checkLogin("refill@example.com", "10.0.0.4");
        }

        clock.advance(Duration.ofMinutes(1));

        assertDoesNotThrow(() ->
                limiter.checkLogin("refill@example.com", "10.0.0.4"));
    }

    @Test
    void differentIpsAreIsolated() {
        for (int i = 0; i < 5; i++) {
            limiter.checkRegister("10.0.0.5");
        }

        assertThrows(
                RateLimitExceededException.class,
                () -> limiter.checkRegister("10.0.0.5")
        );
        assertDoesNotThrow(() -> limiter.checkRegister("10.0.0.6"));
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
