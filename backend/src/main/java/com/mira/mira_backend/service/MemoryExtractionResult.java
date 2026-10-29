package com.mira.mira_backend.service;

import com.mira.mira_backend.entity.MemoryCandidateClassification;

import java.util.List;

public record MemoryExtractionResult(
        List<ExtractedCandidate> candidates
) {
    public MemoryExtractionResult {
        candidates = candidates == null
                ? List.of()
                : List.copyOf(candidates);
    }

    public record ExtractedCandidate(
            String field,
            String value,
            MemoryCandidateClassification classification,
            String evidence
    ) {
    }
}