package com.mira.app.data.model;

public class LogoutRequest {

    private final String refreshToken;

    public LogoutRequest(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}