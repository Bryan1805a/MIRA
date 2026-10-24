package com.mira.app.data.model;

public class SessionResponse {

    private String userId;
    private String email;
    private String sessionId;
    private String tokenType;
    private String accessToken;
    private String accessTokenExpiresAt;
    private String refreshToken;
    private String refreshTokenExpiresAt;

    public String getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getTokenType() {
        return tokenType;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getAccessTokenExpiresAt() {
        return accessTokenExpiresAt;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public String getRefreshTokenExpiresAt() {
        return refreshTokenExpiresAt;
    }
}