package com.mira.mira_backend.ai;

public record AICompletion(
        String content
) {
    public AICompletion {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException(
                    "content must not be blank"
            );
        }
    }
}