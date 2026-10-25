package com.mira.mira_backend.service;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MessageRateLimiter {

    private static final int LIMIT = 10;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final Clock clock;
    private final ConcurrentHashMap<UUID, Window> windows =
            new ConcurrentHashMap<>();

    public MessageRateLimiter() {
        this(Clock.systemUTC());
    }

    MessageRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public void check(UUID userId) {
        Instant now = clock.instant();

        Window window = windows.computeIfAbsent(
                userId,
                ignored -> new Window(now)
        );

        if (!window.tryAcquire(now)) {
            throw new RateLimitExceededException();
        }
    }

    private static final class Window {

        private Instant start;
        private int count;

        private Window(Instant start) {
            this.start = start;
        }

        private synchronized boolean tryAcquire(Instant now) {
            if (!now.isBefore(start.plus(WINDOW))) {
                start = now;
                count = 0;
            }

            if (count >= LIMIT) {
                return false;
            }

            count++;
            return true;
        }
    }
}