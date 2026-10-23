package com.mira.mira_backend.repository;

import com.mira.mira_backend.entity.Message;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MessageRepository
        extends JpaRepository<Message, UUID> {

    @Query("""
            SELECT m
            FROM Message m
            WHERE m.conversation.id = :conversationId
              AND m.conversation.user.id = :userId
            ORDER BY m.createdAt ASC, m.id ASC
            """)
    List<Message> findFirstPage(
            @Param("conversationId") UUID conversationId,
            @Param("userId") UUID userId,
            Pageable pageable
    );

    @Query("""
            SELECT m
            FROM Message m
            WHERE m.conversation.id = :conversationId
              AND m.conversation.user.id = :userId
              AND (
                    m.createdAt > :cursorCreatedAt
                    OR (
                        m.createdAt = :cursorCreatedAt
                        AND m.id > :cursorId
                    )
              )
            ORDER BY m.createdAt ASC, m.id ASC
            """)
    List<Message> findPageAfter(
            @Param("conversationId") UUID conversationId,
            @Param("userId") UUID userId,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") UUID cursorId,
            Pageable pageable
    );

    @Query("""
        SELECT m
        FROM Message m
        WHERE m.conversation.id = :conversationId
          AND m.conversation.user.id = :userId
        ORDER BY m.createdAt DESC, m.id DESC
        """)
    List<Message> findRecent(
            @Param("conversationId") UUID conversationId,
            @Param("userId") UUID userId,
            Pageable pageable
    );
}