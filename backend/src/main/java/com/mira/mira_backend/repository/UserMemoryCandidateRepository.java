package com.mira.mira_backend.repository;

import com.mira.mira_backend.entity.MemoryCandidateStatus;
import com.mira.mira_backend.entity.UserMemoryCandidate;

import org.springframework.data.jpa.repository.JpaRepository;

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
}