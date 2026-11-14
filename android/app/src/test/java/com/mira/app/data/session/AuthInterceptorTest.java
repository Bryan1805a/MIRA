package com.mira.app.data.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

public class AuthInterceptorTest {

    private MockWebServer server;
    private TokenStore tokenStore;

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        server.start();

        tokenStore = new TokenStore(new FakeSharedPreferences());
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    public void addsBearerTokenWhenASessionExists() throws Exception {
        tokenStore.saveSession(
                SessionFixtures.session("access-1", "refresh-1"));

        performRequest(null);

        RecordedRequest request = server.takeRequest();

        assertEquals(
                "Bearer access-1",
                request.getHeader("Authorization")
        );
    }

    @Test
    public void doesNotAddAHeaderWithoutAnAccessToken() throws Exception {
        performRequest(null);

        RecordedRequest request = server.takeRequest();

        assertNull(request.getHeader("Authorization"));
    }

    @Test
    public void preservesAnExistingAuthorizationHeader() throws Exception {
        tokenStore.saveSession(
                SessionFixtures.session("access-1", "refresh-1"));

        performRequest("Custom value");

        RecordedRequest request = server.takeRequest();

        assertEquals(
                "Custom value",
                request.getHeader("Authorization")
        );
    }

    @Test
    public void doesNotAddAHeaderForABlankAccessToken() throws Exception {
        tokenStore.saveSession(
                SessionFixtures.session("", "refresh-1"));

        performRequest(null);

        RecordedRequest request = server.takeRequest();

        assertNull(request.getHeader("Authorization"));
    }

    private void performRequest(String authorization) throws IOException {
        server.enqueue(new MockResponse().setResponseCode(200));

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new AuthInterceptor(tokenStore))
                .build();

        Request.Builder builder = new Request.Builder()
                .url(server.url("/api/v1/me"));

        if (authorization != null) {
            builder.header("Authorization", authorization);
        }

        try (Response response = client.newCall(
                builder.build()
        ).execute()) {
            assertEquals(200, response.code());
        }
    }
}
