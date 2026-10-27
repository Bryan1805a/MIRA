package com.mira.mira_backend.dto;

import com.mira.mira_backend.entity.UserMemoryCandidate;

import java.time.Instant;
import java.util.UUID;

public record MemoryCandidateResponse(
        UUID id,
        String field,
        String value,
        String evidence,
        int evidenceCount,
        Instant createdAt
) {
    public static MemoryCandidateResponse from(
            UserMemoryCandidate candidate
    ) {
        return new MemoryCandidateResponse(
                candidate.getId(),
                candidate.getField(),
                candidate.getValue(),
                candidate.getEvidence(),
                candidate.getEvidenceCount(),
                candidate.getCreatedAt()
        );
    }
}