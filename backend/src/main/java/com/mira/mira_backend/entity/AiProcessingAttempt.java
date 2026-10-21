package com.mira.mira_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "ai_processing_attempt")
public class AiProcessingAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "response_id",
            nullable = false,
            updatable = false
    )
    private AiResponse response;

    @Column(
            name = "attempt_number",
            nullable = false,
            updatable = false
    )
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 20)
    private AttemptOutcome outcome = AttemptOutcome.IN_PROGRESS;

    @Column(name = "error_category", length = 50)
    private String errorCategory;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected AiProcessingAttempt() {
        // Required by JPA.
    }

    public AiProcessingAttempt(
            AiResponse response,
            int attemptNumber
    ) {
        if (attemptNumber < 1) {
            throw new IllegalArgumentException(
                    "attemptNumber must be at least 1"
            );
        }

        this.response = Objects.requireNonNull(
                response,
                "response must not be null"
        );
        this.attemptNumber = attemptNumber;
        this.outcome = AttemptOutcome.IN_PROGRESS;
    }

    @PrePersist
    protected void onCreate() {
        if (startedAt == null) {
            startedAt = Instant.now();
        }

        if (outcome == AttemptOutcome.IN_PROGRESS) {
            finishedAt = null;
        }
    }

    public void succeed() {
        finish(AttemptOutcome.SUCCESS, null);
    }

    public void timeout(String errorCategory) {
        finish(AttemptOutcome.TIMEOUT, errorCategory);
    }

    public void error(String errorCategory) {
        finish(AttemptOutcome.ERROR, errorCategory);
    }

    private void finish(
            AttemptOutcome nextOutcome,
            String nextErrorCategory
    ) {
        if (outcome != AttemptOutcome.IN_PROGRESS) {
            throw new IllegalStateException(
                    "Attempt has already finished."
            );
        }

        outcome = Objects.requireNonNull(
                nextOutcome,
                "outcome must not be null"
        );

        if (nextErrorCategory != null
                && nextErrorCategory.length() > 50) {
            throw new IllegalArgumentException(
                    "errorCategory must be at most 50 characters"
            );
        }

        errorCategory = nextErrorCategory;
        finishedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public AiResponse getResponse() {
        return response;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public AttemptOutcome getOutcome() {
        return outcome;
    }

    public String getErrorCategory() {
        return errorCategory;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }
}