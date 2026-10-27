package com.mira.mira_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "memory_extraction_job")
public class MemoryExtractionJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(
            name = "conversation_id",
            nullable = false,
            updatable = false
    )
    private UUID conversationId;

    @Column(
            name = "response_id",
            nullable = false,
            updatable = false,
            unique = true
    )
    private UUID responseId;

    @Column(
            name = "expected_generation",
            nullable = false,
            updatable = false
    )
    private int expectedGeneration;

    @Column(
            name = "expected_version",
            nullable = false,
            updatable = false
    )
    private int expectedVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MemoryJobStatus status = MemoryJobStatus.PENDING;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "error_category", length = 50)
    private String errorCategory;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected MemoryExtractionJob() {
        // Required by JPA.
    }

    public MemoryExtractionJob(
            UUID userId,
            UUID conversationId,
            UUID responseId,
            int expectedGeneration,
            int expectedVersion
    ) {
        this.userId = Objects.requireNonNull(userId);
        this.conversationId = Objects.requireNonNull(conversationId);
        this.responseId = Objects.requireNonNull(responseId);

        if (expectedGeneration < 1 || expectedVersion < 1) {
            throw new IllegalArgumentException(
                    "Expected generation and version must be positive."
            );
        }

        this.expectedGeneration = expectedGeneration;
        this.expectedVersion = expectedVersion;
        this.status = MemoryJobStatus.PENDING;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }

        if (status == null) {
            status = MemoryJobStatus.PENDING;
        }
    }

    public void markProcessing() {
        if (status != MemoryJobStatus.PENDING) {
            throw new IllegalStateException(
                    "Only a PENDING job can start processing."
            );
        }

        status = MemoryJobStatus.PROCESSING;
        attempts++;
        startedAt = Instant.now();
        finishedAt = null;
        errorCategory = null;
    }

    public void markCompleted() {
        finish(MemoryJobStatus.COMPLETED, null);
    }

    public void markSkipped() {
        finish(MemoryJobStatus.SKIPPED, null);
    }

    public void markFailed(String category) {
        finish(MemoryJobStatus.FAILED, category);
    }

    private void finish(
            MemoryJobStatus nextStatus,
            String category
    ) {
        status = nextStatus;
        errorCategory = category;
        finishedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public UUID getResponseId() {
        return responseId;
    }

    public int getExpectedGeneration() {
        return expectedGeneration;
    }

    public int getExpectedVersion() {
        return expectedVersion;
    }

    public MemoryJobStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getErrorCategory() {
        return errorCategory;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }
}