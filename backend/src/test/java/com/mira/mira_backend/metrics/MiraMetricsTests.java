package com.mira.mira_backend.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MiraMetricsTests {

    private final SimpleMeterRegistry registry =
            new SimpleMeterRegistry();

    private final MiraMetrics metrics = new MiraMetrics(registry);

    @Test
    void recordsChatOutcomes() {
        metrics.chatOutcome("completed");
        metrics.chatOutcome("failed");
        metrics.chatOutcome("failed");

        assertEquals(1.0, counter(
                "mira.chat.requests", "outcome", "completed"));
        assertEquals(2.0, counter(
                "mira.chat.requests", "outcome", "failed"));
    }

    @Test
    void recordsProviderErrorsAndRetries() {
        metrics.chatProviderError("TIMEOUT");
        metrics.chatRetry();
        metrics.chatRetry();

        assertEquals(1.0, counter(
                "mira.chat.provider.errors", "category", "TIMEOUT"));
        assertEquals(2.0, counter("mira.chat.retries"));
    }

    @Test
    void recordsExtractionOutcomes() {
        metrics.extractionJob("COMPLETED");
        metrics.extractionJob("SKIPPED");
        metrics.extractionJob("FAILED");

        assertEquals(1.0, counter(
                "mira.extraction.jobs", "status", "COMPLETED"));
        assertEquals(1.0, counter(
                "mira.extraction.jobs", "status", "SKIPPED"));
        assertEquals(1.0, counter(
                "mira.extraction.jobs", "status", "FAILED"));
    }

    @Test
    void recordsRateLimitsAndMemoryGovernance() {
        metrics.rateLimited("login-email");
        metrics.memoryPurged();
        metrics.candidateReviewed("accept");
        metrics.candidateReviewed("reject");

        assertEquals(1.0, counter(
                "mira.auth.rate_limited", "scope", "login-email"));
        assertEquals(1.0, counter("mira.memory.purges"));
        assertEquals(1.0, counter(
                "mira.memory.candidates", "action", "accept"));
        assertEquals(1.0, counter(
                "mira.memory.candidates", "action", "reject"));
    }

    private double counter(String name, String... tags) {
        var counter = registry.find(name).tags(tags).counter();

        return counter == null ? 0.0 : counter.count();
    }
}
