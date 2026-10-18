package com.mira.mira_backend.dto;

import java.time.Instant;
import java.util.UUID;

public record LoginResponse(
        UUID userId,
        String email,
        UUID sessionId,
        String tokenType,
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt
) {
}