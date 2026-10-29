package com.mira.mira_backend.repository;

import com.mira.mira_backend.entity.MemoryExtractionJob;
import com.mira.mira_backend.entity.MemoryJobStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

import java.time.Instant;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemoryExtractionJobRepository
        extends JpaRepository<MemoryExtractionJob, UUID> {

    Optional<MemoryExtractionJob> findByResponseId(UUID responseId);

    Optional<MemoryExtractionJob>
    findFirstByStatusOrderByCreatedAtAsc(MemoryJobStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT j
            FROM MemoryExtractionJob j
            WHERE j.id = :jobId
            """)
    Optional<MemoryExtractionJob> findByIdForUpdate(
            @Param("jobId") UUID jobId
    );

    @Query(value = """
            SELECT id
            FROM memory_extraction_job
            WHERE status = 'PENDING'
            ORDER BY created_at ASC
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<UUID> findNextPendingIdForUpdate();

    @Query(value = """
            SELECT id
            FROM memory_extraction_job
            WHERE status = 'PROCESSING'
              AND started_at < :cutoff
            ORDER BY started_at ASC
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<UUID> findStaleProcessingIdsForUpdate(
            @Param("cutoff") Instant cutoff
    );
}