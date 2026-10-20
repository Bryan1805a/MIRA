package com.mira.mira_backend.repository;

import com.mira.mira_backend.entity.Conversation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository
        extends JpaRepository<Conversation, UUID> {

    @Query("""
            SELECT c
            FROM Conversation c
            WHERE c.id = :conversationId
              AND c.user.id = :userId
            """)
    Optional<Conversation> findOwnedById(
            @Param("conversationId") UUID conversationId,
            @Param("userId") UUID userId
    );

    @Query("""
            SELECT c
            FROM Conversation c
            WHERE c.user.id = :userId
            ORDER BY c.updatedAt DESC, c.id DESC
            """)
    List<Conversation> findFirstPage(
            @Param("userId") UUID userId,
            Pageable pageable
    );

    @Query("""
            SELECT c
            FROM Conversation c
            WHERE c.user.id = :userId
              AND (
                    c.updatedAt < :cursorUpdatedAt
                    OR (
                        c.updatedAt = :cursorUpdatedAt
                        AND c.id < :cursorId
                    )
              )
            ORDER BY c.updatedAt DESC, c.id DESC
            """)
    List<Conversation> findPageAfter(
            @Param("userId") UUID userId,
            @Param("cursorUpdatedAt") Instant cursorUpdatedAt,
            @Param("cursorId") UUID cursorId,
            Pageable pageable
    );
}