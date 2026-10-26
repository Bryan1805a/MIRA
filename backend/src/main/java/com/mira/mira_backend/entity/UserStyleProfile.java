package com.mira.mira_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "user_style_profile")
public class UserStyleProfile {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            name = "profile",
            nullable = false,
            columnDefinition = "jsonb"
    )
    private Map<String, String> profile = new LinkedHashMap<>();

    @Column(name = "version", nullable = false)
    private int version = 1;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserStyleProfile() {
        // Required by JPA.
    }

    public UserStyleProfile(UUID userId) {
        this.userId = Objects.requireNonNull(
                userId,
                "userId must not be null"
        );
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();

        if (updatedAt == null) {
            updatedAt = now;
        }

        if (version < 1) {
            version = 1;
        }

        if (profile == null) {
            profile = new LinkedHashMap<>();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public void replaceProfile(Map<String, String> nextProfile) {
        profile = new LinkedHashMap<>(
                Objects.requireNonNull(
                        nextProfile,
                        "nextProfile must not be null"
                )
        );
        version++;
    }

    public void setEnabled(boolean nextEnabled) {
        if (enabled != nextEnabled) {
            enabled = nextEnabled;
            version++;
        }
    }

    public void purge() {
        profile = new LinkedHashMap<>();
        version++;
    }

    public UUID getUserId() {
        return userId;
    }

    public Map<String, String> getProfile() {
        return Map.copyOf(profile);
    }

    public int getVersion() {
        return version;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}