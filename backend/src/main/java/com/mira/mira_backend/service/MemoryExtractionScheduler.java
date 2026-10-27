package com.mira.mira_backend.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "mira.memory.extraction.scheduler-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class MemoryExtractionScheduler {

    private final MemoryExtractionWorker worker;

    public MemoryExtractionScheduler(
            MemoryExtractionWorker worker
    ) {
        this.worker = worker;
    }

    @Scheduled(
            fixedDelayString =
                    "${mira.memory.extraction.poll-interval-ms:2000}"
    )
    public void poll() {
        while (worker.processNextJob()) {
            // Drain ready jobs before waiting for the next poll.
        }
    }
}