package com.mira.mira_backend.service;

public class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException() {
        super("An account with this email already exists.");
    }
}
