package com.mira.app.data.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.mira.app.data.model.SessionResponse;

import org.junit.Test;

public class TokenStoreTest {

    private final FakeSharedPreferences preferences =
            new FakeSharedPreferences();

    private final TokenStore store = new TokenStore(preferences);

    @Test
    public void saveSessionPersistsEveryField() {
        SessionResponse session = SessionFixtures.session(
                "access-1",
                "refresh-1"
        );

        store.saveSession(session);

        assertEquals("user-1", store.userId());
        assertEquals("user@example.com", store.email());
        assertEquals("session-1", store.sessionId());
        assertEquals("access-1", store.accessToken());
        assertEquals("refresh-1", store.refreshToken());
        assertTrue(store.hasSession());
    }

    @Test
    public void clearRemovesTheStoredSession() {
        store.saveSession(SessionFixtures.session("access-1", "refresh-1"));

        store.clear();

        assertNull(store.userId());
        assertNull(store.email());
        assertNull(store.sessionId());
        assertNull(store.accessToken());
        assertNull(store.refreshToken());
        assertFalse(store.hasSession());
    }

    @Test
    public void hasSessionRequiresARefreshToken() {
        assertFalse(store.hasSession());

        store.saveSession(SessionFixtures.session("access-1", null));

        assertNull(store.refreshToken());
        assertFalse(store.hasSession());
    }

    @Test
    public void missingSessionReturnsNullValues() {
        assertNull(store.userId());
        assertNull(store.email());
        assertNull(store.sessionId());
        assertNull(store.accessToken());
        assertNull(store.refreshToken());
    }
}
