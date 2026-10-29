package com.mira.mira_backend.ai;

import java.util.List;

public record AIRequest(
        List<AIMessage> messages,
        boolean jsonResponse
) {
    public AIRequest {
        messages = messages == null ? List.of() : List.copyOf(messages);

        if (messages.isEmpty()) {
            throw new IllegalArgumentException(
                    "messages must not be empty"
            );
        }
    }

    public AIRequest(List<AIMessage> messages) {
        this(messages, false);
    }
}