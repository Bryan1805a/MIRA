package com.mira.mira_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank
        @Size(max = 254)
        String email,

        @NotBlank
        @Size(max = 128)
        String password
) {
}