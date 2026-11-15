package com.mira.mira_backend.metrics;

import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.stereotype.Component;

@Component
public class MiraMetrics {

    private final MeterRegistry registry;

    public MiraMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void chatOutcome(String outcome) {
        registry.counter("mira.chat.requests", "outcome", outcome)
                .increment();
    }

    public void chatProviderError(String category) {
        registry.counter(
                "mira.chat.provider.errors",
                "category",
                category
        ).increment();
    }

    public void chatRetry() {
        registry.counter("mira.chat.retries").increment();
    }

    public void chatRegenerate() {
        registry.counter("mira.chat.regenerations").increment();
    }

    public void extractionJob(String status) {
        registry.counter("mira.extraction.jobs", "status", status)
                .increment();
    }

    public void rateLimited(String scope) {
        registry.counter("mira.auth.rate_limited", "scope", scope)
                .increment();
    }

    public void memoryPurged() {
        registry.counter("mira.memory.purges").increment();
    }

    public void candidateReviewed(String action) {
        registry.counter("mira.memory.candidates", "action", action)
                .increment();
    }
}
