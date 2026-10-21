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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "ai_response")
public class AiResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private UserAccount user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "conversation_id",
            nullable = false,
            updatable = false
    )
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_message_id",
            nullable = false,
            updatable = false
    )
    private Message userMessage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assistant_message_id")
    private Message assistantMessage;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AiResponseStatus status = AiResponseStatus.PENDING;

    @Column(name = "operation", nullable = false, length = 40, updatable = false)
    private String operation;

    @Column(
            name = "idempotency_key",
            nullable = false,
            length = 128,
            updatable = false
    )
    private String idempotencyKey;

    @Column(
            name = "request_hash",
            nullable = false,
            length = 64,
            updatable = false
    )
    private String requestHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AiResponse() {
        // Required by JPA.
    }

    public AiResponse(
            UserAccount user,
            Conversation conversation,
            Message userMessage,
            String operation,
            String idempotencyKey,
            String requestHash
    ) {
        this.user = Objects.requireNonNull(user, "user must not be null");
        this.conversation = Objects.requireNonNull(
                conversation,
                "conversation must not be null"
        );
        this.userMessage = Objects.requireNonNull(
                userMessage,
                "userMessage must not be null"
        );
        this.operation = requireText(operation, "operation", 40);
        this.idempotencyKey = requireText(
                idempotencyKey,
                "idempotencyKey",
                128
        );
        this.requestHash = requireText(requestHash, "requestHash", 64);
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

        if (status == null) {
            status = AiResponseStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public void markPending() {
        if (status != AiResponseStatus.PENDING
                && status != AiResponseStatus.FAILED
                && status != AiResponseStatus.UNKNOWN) {
            throw new IllegalStateException(
                    "Only PENDING, FAILED, or UNKNOWN responses can be retried."
            );
        }

        assistantMessage = null;
        status = AiResponseStatus.PENDING;
    }

    public void markCompleted(Message assistantMessage) {
        if (status != AiResponseStatus.PENDING) {
            throw new IllegalStateException(
                    "Only a PENDING response can be completed."
            );
        }

        this.assistantMessage = Objects.requireNonNull(
                assistantMessage,
                "assistantMessage must not be null"
        );
        status = AiResponseStatus.COMPLETED;
    }

    public void markFailed() {
        if (status != AiResponseStatus.PENDING) {
            throw new IllegalStateException(
                    "Only a PENDING response can fail."
            );
        }

        assistantMessage = null;
        status = AiResponseStatus.FAILED;
    }

    public void markUnknown() {
        if (status != AiResponseStatus.PENDING) {
            throw new IllegalStateException(
                    "Only a PENDING response can become unknown."
            );
        }

        assistantMessage = null;
        status = AiResponseStatus.UNKNOWN;
    }

    private static String requireText(
            String value,
            String field,
            int maxLength
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " must not be blank"
            );
        }

        if (value.length() > maxLength) {
            throw new IllegalArgumentException(
                    field + " must be at most " + maxLength + " characters"
            );
        }

        return value;
    }

    public UUID getId() {
        return id;
    }

    public UserAccount getUser() {
        return user;
    }

    public Conversation getConversation() {
        return conversation;
    }

    public Message getUserMessage() {
        return userMessage;
    }

    public Message getAssistantMessage() {
        return assistantMessage;
    }

    public AiResponseStatus getStatus() {
        return status;
    }

    public String getOperation() {
        return operation;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}