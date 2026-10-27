package com.mira.mira_backend.service;

public class MemoryCandidateNotFoundException extends RuntimeException {

    public MemoryCandidateNotFoundException() {
        super("Memory candidate not found.");
    }
}