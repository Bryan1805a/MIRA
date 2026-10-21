package com.mira.mira_backend.dto;

import java.util.List;

public record ApiErrorResponse(
        String error,
        String message,
        String requestId,
        List<Detail> details
) {
    public ApiErrorResponse {
        details = details == null ? List.of() : List.copyOf(details);
    }

    public ApiErrorResponse(String error, String message) {
        this(error, message, null, List.of());
    }

    public record Detail(
            String field,
            String reason
    ) {
    }
}