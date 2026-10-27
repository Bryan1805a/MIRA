package com.mira.mira_backend.dto;

import java.util.List;

public record MemoryCandidateListResponse(
        List<MemoryCandidateResponse> items
) {
    public MemoryCandidateListResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}