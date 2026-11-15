package com.mira.mira_backend.dto;

/**
 * Regeneration result. {@code regenerated} is false when the provider failed and
 * the previous answer was kept, so the client can say so instead of silently
 * showing the unchanged text.
 */
public record RegenerateResponse(
        AssistantResponseResponse assistantResponse,
        boolean regenerated
) {
}
