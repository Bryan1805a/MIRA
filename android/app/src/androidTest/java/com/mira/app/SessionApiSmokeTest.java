package com.mira.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.gson.Gson;
import com.mira.app.data.model.ConversationPageResponse;
import com.mira.app.data.model.ConversationResponse;
import com.mira.app.data.model.CreateConversationRequest;
import com.mira.app.data.model.LoginRequest;
import com.mira.app.data.model.ResponseStatus;
import com.mira.app.data.model.SendMessageRequest;
import com.mira.app.data.model.SendMessageResponse;
import com.mira.app.data.model.SessionResponse;
import com.mira.app.data.remote.MiraApi;
import com.mira.app.data.session.AuthInterceptor;
import com.mira.app.data.session.TokenStore;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

@RunWith(AndroidJUnit4.class)
public class SessionApiSmokeTest {

    private MockWebServer server;
    private TokenStore tokenStore;
    private MiraApi api;

    @Before
    public void setUp() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation()
                .getTargetContext();

        tokenStore = TokenStore.get(context);
        tokenStore.clear();

        server = new MockWebServer();
        server.start();

        api = new Retrofit.Builder()
                .baseUrl(server.url("/"))
                .addConverterFactory(
                        GsonConverterFactory.create(new Gson())
                )
                .client(new OkHttpClient.Builder()
                        .addInterceptor(new AuthInterceptor(tokenStore))
                        .build())
                .build()
                .create(MiraApi.class);
    }

    @After
    public void tearDown() throws Exception {
        tokenStore.clear();
        server.shutdown();
    }

    @Test
    public void loginThenListCreateAndSendMessage() throws Exception {
        server.enqueue(json(200, loginBody()));
        server.enqueue(json(200, conversationsBody()));
        server.enqueue(json(201, conversationBody()));
        server.enqueue(json(201, sendMessageBody()));

        SessionResponse session = api.login(
                new LoginRequest(
                        "smoke@example.com",
                        "Correct-Horse-123!"
                )
        ).execute().body();

        assertNotNull(session);
        tokenStore.saveSession(session);

        RecordedRequest loginRequest = server.takeRequest();
        assertNull(loginRequest.getHeader("Authorization"));

        ConversationPageResponse page = api.conversations(20, null)
                .execute()
                .body();

        assertNotNull(page);
        assertEquals(1, page.getItems().size());
        assertEquals(
                "Bearer access-1",
                server.takeRequest().getHeader("Authorization")
        );

        ConversationResponse conversation = api.createConversation(
                new CreateConversationRequest("Smoke")
        ).execute().body();

        assertNotNull(conversation);
        assertEquals(
                "Bearer access-1",
                server.takeRequest().getHeader("Authorization")
        );

        SendMessageResponse sent = api.sendMessage(
                conversation.getId(),
                "idem-1",
                new SendMessageRequest("Hello")
        ).execute().body();

        assertNotNull(sent);
        assertEquals(
                ResponseStatus.COMPLETED,
                sent.getAssistantResponse().getStatus()
        );

        RecordedRequest sendRequest = server.takeRequest();

        assertEquals(
                "Bearer access-1",
                sendRequest.getHeader("Authorization")
        );
        assertEquals(
                "idem-1",
                sendRequest.getHeader("Idempotency-Key")
        );
    }

    private MockResponse json(int status, String body) {
        return new MockResponse()
                .setResponseCode(status)
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }

    private String loginBody() {
        return "{"
                + "\"userId\":\"user-1\","
                + "\"email\":\"smoke@example.com\","
                + "\"sessionId\":\"session-1\","
                + "\"tokenType\":\"Bearer\","
                + "\"accessToken\":\"access-1\","
                + "\"accessTokenExpiresAt\":\"2026-11-14T00:00:00Z\","
                + "\"refreshToken\":\"refresh-1\","
                + "\"refreshTokenExpiresAt\":\"2026-12-14T00:00:00Z\""
                + "}";
    }

    private String conversationsBody() {
        return "{"
                + "\"items\":["
                + conversationBody()
                + "],"
                + "\"nextCursor\":null"
                + "}";
    }

    private String conversationBody() {
        return "{"
                + "\"id\":\"conversation-1\","
                + "\"title\":\"Smoke\","
                + "\"createdAt\":\"2026-11-14T00:00:00Z\","
                + "\"updatedAt\":\"2026-11-14T00:00:00Z\""
                + "}";
    }

    private String sendMessageBody() {
        return "{"
                + "\"userMessage\":{"
                + "\"id\":\"message-1\","
                + "\"role\":\"USER\","
                + "\"content\":\"Hello\","
                + "\"createdAt\":\"2026-11-14T00:00:00Z\""
                + "},"
                + "\"assistantResponse\":{"
                + "\"id\":\"response-1\","
                + "\"status\":\"COMPLETED\","
                + "\"content\":\"Hi\","
                + "\"createdAt\":\"2026-11-14T00:00:01Z\""
                + "}"
                + "}";
    }
}
