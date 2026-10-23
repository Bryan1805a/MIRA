package com.mira.mira_backend.dto;

import java.util.List;

public record MessagePageResponse(
        List<MessageResponse> items,
        String nextCursor
) {
    public MessagePageResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}