package com.mira.mira_backend.dto;

public record ApiErrorResponse(
        String error,
        String message
) {
}
