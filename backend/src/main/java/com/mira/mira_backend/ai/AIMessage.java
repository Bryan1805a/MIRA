package com.mira.mira_backend.ai;

import java.util.Objects;

public record AIMessage(
        AIRole role,
        String content
) {
    public AIMessage {
        role = Objects.requireNonNull(role, "role must not be null");

        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException(
                    "content must not be blank"
            );
        }
    }
}