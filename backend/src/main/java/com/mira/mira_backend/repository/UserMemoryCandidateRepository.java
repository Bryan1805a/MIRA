package com.mira.mira_backend.repository;

import com.mira.mira_backend.entity.MemoryCandidateStatus;
import com.mira.mira_backend.entity.UserMemoryCandidate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserMemoryCandidateRepository
        extends JpaRepository<UserMemoryCandidate, UUID> {

    Optional<UserMemoryCandidate>
    findByUserIdAndFieldAndValueAndStatus(
            UUID userId,
            String field,
            String value,
            MemoryCandidateStatus status
    );

    List<UserMemoryCandidate>
    findByUserIdAndStatusOrderByCreatedAtDesc(
            UUID userId,
            MemoryCandidateStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT c
            FROM UserMemoryCandidate c
            WHERE c.id = :candidateId
              AND c.userId = :userId
            """)
    Optional<UserMemoryCandidate> findByIdAndUserIdForUpdate(
            @Param("candidateId") UUID candidateId,
            @Param("userId") UUID userId
    );

    @Modifying
    @Query("""
            DELETE FROM UserMemoryCandidate c
            WHERE c.userId = :userId
              AND c.status = :status
            """)
    int deleteByUserIdAndStatus(
            @Param("userId") UUID userId,
            @Param("status") MemoryCandidateStatus status
    );
}