package com.mira.mira_backend.repository;

import com.mira.mira_backend.entity.AiProcessingAttempt;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AiProcessingAttemptRepository
        extends JpaRepository<AiProcessingAttempt, UUID> {

    List<AiProcessingAttempt>
    findByResponseIdOrderByAttemptNumberAsc(UUID responseId);

    @Query("""
            SELECT COALESCE(MAX(a.attemptNumber), 0)
            FROM AiProcessingAttempt a
            WHERE a.response.id = :responseId
            """)
    int findMaxAttemptNumber(
            @Param("responseId") UUID responseId
    );
}