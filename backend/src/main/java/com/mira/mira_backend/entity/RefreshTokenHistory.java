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
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "refresh_token_history",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_refresh_token_history_hash",
                columnNames = "token_hash"
        )
)
public class RefreshTokenHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, updatable = false)
    private UserSession session;

    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    private String tokenHash;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    protected RefreshTokenHistory() {
        // Required by JPA.
    }

    public RefreshTokenHistory(
            UserSession session,
            String tokenHash,
            Instant issuedAt
    ) {
        this.session = Objects.requireNonNull(session, "session must not be null");
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash must not be null");
        this.issuedAt = Objects.requireNonNull(issuedAt, "issuedAt must not be null");
    }

    @PrePersist
    protected void onCreate() {
        if (issuedAt == null) {
            issuedAt = Instant.now();
        }
    }

    public void consume(Instant consumedAt) {
        Objects.requireNonNull(consumedAt, "consumedAt must not be null");

        if (this.consumedAt != null) {
            throw new IllegalStateException("Refresh token has already been consumed");
        }

        if (consumedAt.isBefore(issuedAt)) {
            throw new IllegalArgumentException("Consumed time cannot precede issue time");
        }

        this.consumedAt = consumedAt;
    }

    public UUID getId() {
        return id;
    }

    public UserSession getSession() {
        return session;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }
}
