
package com.mira.mira_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "user_session")
public class UserSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private UserAccount user;

    @Column(name = "refresh_token_hash", nullable = false, length = 64)
    private String refreshTokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    protected UserSession() {
        // Required by JPA.
    }

    public UserSession(
            UserAccount user,
            String refreshTokenHash,
            Instant expiresAt
    ) {
        this.user = Objects.requireNonNull(user, "user must not be null");
        this.refreshTokenHash = Objects.requireNonNull(
                refreshTokenHash,
                "refreshTokenHash must not be null"
        );
        this.expiresAt = Objects.requireNonNull(
                expiresAt,
                "expiresAt must not be null"
        );
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }

        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalStateException(
                    "Session expiry must be after creation time"
            );
        }
    }

    public void rotateRefreshTokenHash(
            String newTokenHash,
            Instant usedAt
    ) {
        this.refreshTokenHash = Objects.requireNonNull(
                newTokenHash,
                "newTokenHash must not be null"
        );
        this.lastUsedAt = Objects.requireNonNull(
                usedAt,
                "usedAt must not be null"
        );
    }

    public void revoke(Instant revokedAt) {
        if (this.revokedAt == null) {
            this.revokedAt = Objects.requireNonNull(
                    revokedAt,
                    "revokedAt must not be null"
            );
        }
    }

    public UUID getId() {
        return id;
    }

    public UserAccount getUser() {
        return user;
    }

    public String getRefreshTokenHash() {
        return refreshTokenHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }
}
