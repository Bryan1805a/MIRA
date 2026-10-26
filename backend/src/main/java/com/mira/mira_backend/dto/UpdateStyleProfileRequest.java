package com.mira.mira_backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record UpdateStyleProfileRequest(
        @NotNull
        @Min(1)
        Integer expectedVersion,

        @NotNull
        Map<String, String> profile
) {
}