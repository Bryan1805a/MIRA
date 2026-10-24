package com.mira.app.data.model;

public class RefreshRequest {

    private final String refreshToken;

    public RefreshRequest(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}