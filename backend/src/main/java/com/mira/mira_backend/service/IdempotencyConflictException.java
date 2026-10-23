package com.mira.mira_backend.service;

public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException() {
        super(
                "Idempotency-Key was already used with a different payload."
        );
    }
}