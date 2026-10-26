package com.mira.mira_backend.service;

public class VersionConflictException extends RuntimeException {

    private final int currentVersion;

    public VersionConflictException(int currentVersion) {
        super("The style profile was updated by another request.");
        this.currentVersion = currentVersion;
    }

    public int currentVersion() {
        return currentVersion;
    }
}