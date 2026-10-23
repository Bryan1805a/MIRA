package com.mira.mira_backend.dto;

public record SendMessageResponse(
        MessageResponse userMessage,
        AssistantResponseResponse assistantResponse
) {
}