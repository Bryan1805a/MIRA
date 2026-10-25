package com.mira.mira_backend.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MessageRateLimiterTests {

    private final MutableClock clock = new MutableClock(
            Instant.parse("2026-10-25T12:00:00Z")
    );

    private final MessageRateLimiter limiter =
            new MessageRateLimiter(clock);

    @Test
    void allowsTenRequestsThenRejectsTheEleventh() {
        UUID user = UUID.randomUUID();

        for (int i = 0; i < 10; i++) {
            assertDoesNotThrow(() -> limiter.check(user));
        }

        assertThrows(
                RateLimitExceededException.class,
                () -> limiter.check(user)
        );
    }

    @Test
    void refillsAfterTheWindow() {
        UUID user = UUID.randomUUID();

        for (int i = 0; i < 10; i++) {
            limiter.check(user);
        }

        clock.advance(Duration.ofMinutes(1));

        assertDoesNotThrow(() -> limiter.check(user));
    }

    @Test
    void isolatesDifferentUsers() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        for (int i = 0; i < 10; i++) {
            limiter.check(first);
        }

        assertThrows(
                RateLimitExceededException.class,
                () -> limiter.check(first)
        );
        assertDoesNotThrow(() -> limiter.check(second));
    }

    @Test
    void allowsExactlyTheLimit() {
        UUID user = UUID.randomUUID();

        for (int i = 0; i < 10; i++) {
            assertDoesNotThrow(() -> limiter.check(user));
        }
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