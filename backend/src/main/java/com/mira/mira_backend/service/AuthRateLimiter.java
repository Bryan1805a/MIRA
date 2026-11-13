package com.mira.mira_backend.service;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AuthRateLimiter {

    private final int loginEmailLimit;
    private final int loginIpLimit;
    private final int registerIpLimit;
    private final int refreshIpLimit;
    private final Duration window;
    private final boolean trustForwardedHeaders;
    private final Clock clock;

    private final ConcurrentHashMap<String, Window> windows =
            new ConcurrentHashMap<>();

    @Autowired
    public AuthRateLimiter(
            @Value("${mira.auth.rate-limit.login-email-limit:5}")
            int loginEmailLimit,
            @Value("${mira.auth.rate-limit.login-ip-limit:30}")
            int loginIpLimit,
            @Value("${mira.auth.rate-limit.register-ip-limit:5}")
            int registerIpLimit,
            @Value("${mira.auth.rate-limit.refresh-ip-limit:60}")
            int refreshIpLimit,
            @Value("${mira.auth.rate-limit.window-seconds:60}")
            long windowSeconds,
            @Value("${mira.auth.rate-limit.trust-forwarded-headers:false}")
            boolean trustForwardedHeaders
    ) {
        this(
                loginEmailLimit,
                loginIpLimit,
                registerIpLimit,
                refreshIpLimit,
                Duration.ofSeconds(windowSeconds),
                trustForwardedHeaders,
                Clock.systemUTC()
        );
    }

    AuthRateLimiter(
            int loginEmailLimit,
            int loginIpLimit,
            int registerIpLimit,
            int refreshIpLimit,
            Duration window,
            boolean trustForwardedHeaders,
            Clock clock
    ) {
        this.loginEmailLimit = loginEmailLimit;
        this.loginIpLimit = loginIpLimit;
        this.registerIpLimit = registerIpLimit;
        this.refreshIpLimit = refreshIpLimit;
        this.window = window;
        this.trustForwardedHeaders = trustForwardedHeaders;
        this.clock = clock;
    }

    public void checkLogin(String email, String clientIp) {
        if (email != null && !email.isBlank()) {
            acquire("login:email:" + normalizeEmail(email), loginEmailLimit);
        }

        acquire("login:ip:" + safe(clientIp), loginIpLimit);
    }

    public void checkRegister(String clientIp) {
        acquire("register:ip:" + safe(clientIp), registerIpLimit);
    }

    public void checkRefresh(String clientIp) {
        acquire("refresh:ip:" + safe(clientIp), refreshIpLimit);
    }

    public String clientIp(HttpServletRequest request) {
        if (trustForwardedHeaders) {
            String forwarded = request.getHeader("X-Forwarded-For");

            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }

        return request.getRemoteAddr();
    }

    @Scheduled(
            fixedDelayString =
                    "${mira.auth.rate-limit.cleanup-interval-ms:600000}"
    )
    public void removeExpiredWindows() {
        Instant now = clock.instant();

        windows.entrySet().removeIf(
                entry -> entry.getValue().isExpired(now, window)
        );
    }

    private void acquire(String key, int limit) {
        if (limit <= 0) {
            return;
        }

        Instant now = clock.instant();

        Window window = windows.computeIfAbsent(
                key,
                ignored -> new Window(now)
        );

        if (!window.tryAcquire(now, limit, this.window)) {
            throw new RateLimitExceededException();
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private static final class Window {

        private Instant start;
        private int count;

        private Window(Instant start) {
            this.start = start;
        }

        private synchronized boolean tryAcquire(
                Instant now,
                int limit,
                Duration window
        ) {
            if (!now.isBefore(start.plus(window))) {
                start = now;
                count = 0;
            }

            if (count >= limit) {
                return false;
            }

            count++;
            return true;
        }

        private synchronized boolean isExpired(
                Instant now,
                Duration window
        ) {
            return !now.isBefore(start.plus(window));
        }
    }
}
