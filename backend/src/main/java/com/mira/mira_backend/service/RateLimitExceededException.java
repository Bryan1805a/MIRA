package com.mira.mira_backend.service;

public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException() {
        super("Rate limit exceeded. Try again shortly.");
    }
}