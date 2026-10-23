package com.mira.mira_backend.dto;

import com.mira.mira_backend.entity.AiResponseStatus;

import java.time.Instant;
import java.util.UUID;

public record AssistantResponseResponse(
        UUID id,
        AiResponseStatus status,
        String content,
        Instant createdAt
) {
}