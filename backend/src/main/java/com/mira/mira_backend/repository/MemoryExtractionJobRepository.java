package com.mira.mira_backend.repository;

import com.mira.mira_backend.entity.MemoryExtractionJob;
import com.mira.mira_backend.entity.MemoryJobStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

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
}