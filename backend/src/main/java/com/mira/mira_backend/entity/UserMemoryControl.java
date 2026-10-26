package com.mira.mira_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "user_memory_control")
public class UserMemoryControl {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "generation", nullable = false)
    private int generation = 1;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserMemoryControl() {
        // Required by JPA.
    }

    public UserMemoryControl(UUID userId) {
        this.userId = Objects.requireNonNull(
                userId,
                "userId must not be null"
        );
    }

    @PrePersist
    protected void onCreate() {
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public void incrementGeneration() {
        generation++;
    }

    public UUID getUserId() {
        return userId;
    }

    public int getGeneration() {
        return generation;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}