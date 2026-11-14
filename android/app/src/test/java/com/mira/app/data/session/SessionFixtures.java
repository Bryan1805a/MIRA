package com.mira.app.data.session;

import com.google.gson.Gson;
import com.mira.app.data.model.SessionResponse;

import java.util.LinkedHashMap;
import java.util.Map;

final class SessionFixtures {

    private SessionFixtures() {
    }

    static SessionResponse session(
            String accessToken,
            String refreshToken
    ) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("userId", "user-1");
        values.put("email", "user@example.com");
        values.put("sessionId", "session-1");
        values.put("tokenType", "Bearer");
        values.put("accessTokenExpiresAt", "2026-11-14T00:00:00Z");
        values.put("refreshTokenExpiresAt", "2026-12-14T00:00:00Z");

        if (accessToken != null) {
            values.put("accessToken", accessToken);
        }

        if (refreshToken != null) {
            values.put("refreshToken", refreshToken);
        }

        Gson gson = new Gson();

        return gson.fromJson(
                gson.toJson(values),
                SessionResponse.class
        );
    }
}
