package com.mira.mira_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "user_memory_candidate")
public class UserMemoryCandidate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "field", nullable = false, length = 50)
    private String field;

    @Column(name = "value", nullable = false, length = 50)
    private String value;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "classification",
            nullable = false,
            length = 30
    )
    private MemoryCandidateClassification classification;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "evidence", columnDefinition = "text")
    private String evidence;

    @Column(name = "evidence_count", nullable = false)
    private int evidenceCount = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MemoryCandidateStatus status = MemoryCandidateStatus.PENDING;

    @Column(name = "generation", nullable = false)
    private int generation;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserMemoryCandidate() {
        // Required by JPA.
    }

    public UserMemoryCandidate(
            UUID userId,
            String field,
            String value,
            MemoryCandidateClassification classification,
            String evidence,
            int generation
    ) {
        this.userId = Objects.requireNonNull(userId);
        this.field = Objects.requireNonNull(field);
        this.value = Objects.requireNonNull(value);
        this.classification = Objects.requireNonNull(classification);
        this.evidence = evidence;
        this.generation = generation;
        this.status = MemoryCandidateStatus.PENDING;
        this.evidenceCount = 1;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();

        if (createdAt == null) {
            createdAt = now;
        }

        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public void recordEvidence(String evidence) {
        evidenceCount++;
        this.evidence = evidence;
    }

    public void accept() {
        status = MemoryCandidateStatus.ACCEPTED;
    }

    public void reject() {
        status = MemoryCandidateStatus.REJECTED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getField() {
        return field;
    }

    public String getValue() {
        return value;
    }

    public MemoryCandidateClassification getClassification() {
        return classification;
    }

    public String getEvidence() {
        return evidence;
    }

    public int getEvidenceCount() {
        return evidenceCount;
    }

    public MemoryCandidateStatus getStatus() {
        return status;
    }

    public int getGeneration() {
        return generation;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}