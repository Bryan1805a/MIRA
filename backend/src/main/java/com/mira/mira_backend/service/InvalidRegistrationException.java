package com.mira.mira_backend.service;

public class InvalidRegistrationException extends RuntimeException {

    public InvalidRegistrationException(String message) {
        super(message);
    }
}
