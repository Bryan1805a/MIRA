package com.mira.mira_backend.repository;

import com.mira.mira_backend.entity.AiResponse;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AiResponseRepository
        extends JpaRepository<AiResponse, UUID> {

    Optional<AiResponse> findByUserIdAndOperationAndIdempotencyKey(
            UUID userId,
            String operation,
            String idempotencyKey
    );

    Optional<AiResponse> findByIdAndUserIdAndConversationId(
            UUID responseId,
            UUID userId,
            UUID conversationId
    );

    Optional<AiResponse> findByUserMessageId(UUID userMessageId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT r
            FROM AiResponse r
            WHERE r.id = :responseId
              AND r.user.id = :userId
              AND r.conversation.id = :conversationId
            """)
    Optional<AiResponse> findOwnedByIdForUpdate(
            @Param("responseId") UUID responseId,
            @Param("userId") UUID userId,
            @Param("conversationId") UUID conversationId
    );

    @Query("""
        SELECT r
        FROM AiResponse r
        JOIN FETCH r.userMessage
        JOIN FETCH r.assistantMessage
        WHERE r.id = :responseId
        """)
    Optional<AiResponse> findWithMessages(
            @Param("responseId") UUID responseId
    );
}