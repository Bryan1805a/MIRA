package com.mira.mira_backend.ai;

import java.util.Objects;

public class AIProviderException extends RuntimeException {

    private final AIErrorCategory category;

    public AIProviderException(
            AIErrorCategory category,
            String message
    ) {
        super(message);
        this.category = Objects.requireNonNull(category);
    }

    public AIProviderException(
            AIErrorCategory category,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.category = Objects.requireNonNull(category);
    }

    public AIErrorCategory category() {
        return category;
    }
}