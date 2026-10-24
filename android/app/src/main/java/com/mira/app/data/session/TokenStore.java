package com.mira.app.data.session;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.mira.app.data.model.SessionResponse;

public final class TokenStore {

    private static final String PREFS_NAME = "mira_session";
    private static final String KEY_MASTER = "mira_master_key";

    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_SESSION_ID = "session_id";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";

    private static volatile TokenStore instance;

    private final SharedPreferences preferences;

    private TokenStore(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            preferences = EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to initialize secure token storage.",
                    exception
            );
        }
    }

    public static TokenStore get(Context context) {
        TokenStore local = instance;

        if (local == null) {
            synchronized (TokenStore.class) {
                local = instance;

                if (local == null) {
                    local = new TokenStore(
                            context.getApplicationContext()
                    );
                    instance = local;
                }
            }
        }

        return local;
    }

    public void saveSession(SessionResponse session) {
        preferences.edit()
                .putString(KEY_USER_ID, session.getUserId())
                .putString(KEY_EMAIL, session.getEmail())
                .putString(KEY_SESSION_ID, session.getSessionId())
                .putString(KEY_ACCESS_TOKEN, session.getAccessToken())
                .putString(KEY_REFRESH_TOKEN, session.getRefreshToken())
                .apply();
    }

    public void clear() {
        preferences.edit().clear().apply();
    }

    public boolean hasSession() {
        return refreshToken() != null;
    }

    public String userId() {
        return preferences.getString(KEY_USER_ID, null);
    }

    public String email() {
        return preferences.getString(KEY_EMAIL, null);
    }

    public String sessionId() {
        return preferences.getString(KEY_SESSION_ID, null);
    }

    public String accessToken() {
        return preferences.getString(KEY_ACCESS_TOKEN, null);
    }

    public String refreshToken() {
        return preferences.getString(KEY_REFRESH_TOKEN, null);
    }
}