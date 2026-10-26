package com.mira.mira_backend.dto;

import java.time.Instant;

public record MemorySettingsResponse(
        boolean enabled,
        int version,
        Instant updatedAt
) {
}