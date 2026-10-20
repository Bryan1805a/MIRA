package com.mira.mira_backend.service;

public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException() {
        super("User account not found.");
    }
}