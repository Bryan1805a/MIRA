package com.mira.mira_backend.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateMemorySettingsRequest(
        @NotNull
        Boolean enabled
) {
}