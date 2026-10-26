package com.mira.mira_backend.dto;

import java.time.Instant;
import java.util.Map;

public record StyleProfileResponse(
        boolean enabled,
        int version,
        Map<String, String> profile,
        Instant updatedAt
) {
    public StyleProfileResponse {
        profile = profile == null ? Map.of() : Map.copyOf(profile);
    }
}