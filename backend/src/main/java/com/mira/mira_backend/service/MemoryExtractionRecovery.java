package com.mira.mira_backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@ConditionalOnProperty(
        name = "mira.memory.extraction.scheduler-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class MemoryExtractionRecovery {

    private final MemoryExtractionService extractionService;
    private final int maxAttempts;
    private final long staleAfterSeconds;

    public MemoryExtractionRecovery(
            MemoryExtractionService extractionService,
            @Value("${mira.memory.extraction.max-attempts:3}")
            int maxAttempts,
            @Value("${mira.memory.extraction.stale-after-seconds:300}")
            long staleAfterSeconds
    ) {
        this.extractionService = extractionService;
        this.maxAttempts = maxAttempts;
        this.staleAfterSeconds = staleAfterSeconds;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        requeueStaleJobs();
    }

    @Scheduled(
            fixedDelayString =
                    "${mira.memory.extraction.recovery-interval-ms:60000}"
    )
    public void poll() {
        requeueStaleJobs();
    }

    private void requeueStaleJobs() {
        extractionService.requeueStaleProcessing(
                Instant.now().minusSeconds(staleAfterSeconds),
                maxAttempts
        );
    }
}