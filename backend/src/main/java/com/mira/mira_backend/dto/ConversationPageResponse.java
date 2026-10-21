package com.mira.mira_backend.dto;

import java.util.List;

public record ConversationPageResponse(
        List<ConversationResponse> items,
        String nextCursor
) {
    public ConversationPageResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}