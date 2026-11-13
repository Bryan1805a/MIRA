package com.mira.mira_backend.auth;

import com.mira.mira_backend.ai.MockAIProvider;
import com.mira.mira_backend.dto.LoginRequest;
import com.mira.mira_backend.support.MemoryIntegrationTestSupport;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class LogSanitizationTest extends MemoryIntegrationTestSupport {

    @Test
    void loginDoesNotLogCredentialsOrTokens() throws Exception {
        String email = "log-login-" + UUID.randomUUID()
                + "@example.com";
        String password = "Correct-Horse-123!";

        register(email, password);

        ListAppender<ILoggingEvent> appender = startCapture();

        try {
            LoginTokens tokens = login(email, password);

            assertNoLeak(appender, email, password,
                    tokens.accessToken(), tokens.refreshToken());
        } finally {
            stopCapture(appender);
        }
    }

    @Test
    void failedLoginDoesNotLogCredentials() throws Exception {
        String email = "log-failed-" + UUID.randomUUID()
                + "@example.com";
        String password = "Wrong-Password-123!";

        ListAppender<ILoggingEvent> appender = startCapture();

        try {
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                            new LoginRequest(email, password))));

            assertNoLeak(appender, email, password);
        } finally {
            stopCapture(appender);
        }
    }

    @Test
    void chatAndProviderFailureDoNotLogPromptsOrCompletions()
            throws Exception {
        LoginTokens user = newUser("log-chat");
        UUID conversationId = createConversation(
                user.accessToken(),
                "Log sanitization"
        );
        String promptSentinel = "PROMPT-" + UUID.randomUUID();
        String completionSentinel = "COMPLETION-" + UUID.randomUUID();

        ListAppender<ILoggingEvent> appender = startCapture();

        try {
            mockAIProvider.setContent(completionSentinel);
            sendMessage(
                    user.accessToken(),
                    conversationId,
                    "Please remember " + promptSentinel,
                    "log-chat-" + UUID.randomUUID()
            );

            mockAIProvider.setScenario(
                    MockAIProvider.Scenario.UNAVAILABLE);
            sendMessage(
                    user.accessToken(),
                    conversationId,
                    "Another " + promptSentinel,
                    "log-chat-fail-" + UUID.randomUUID()
            );

            assertNoLeak(appender, promptSentinel, completionSentinel);
        } finally {
            stopCapture(appender);
        }
    }

    private ListAppender<ILoggingEvent> startCapture() {
        Logger root = (Logger) LoggerFactory.getLogger(
                Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        return appender;
    }

    private void stopCapture(ListAppender<ILoggingEvent> appender) {
        Logger root = (Logger) LoggerFactory.getLogger(
                Logger.ROOT_LOGGER_NAME);
        root.detachAppender(appender);
    }

    private void assertNoLeak(
            ListAppender<ILoggingEvent> appender,
            String... secrets) {
        for (String secret : secrets) {
            assertTrue(
                    appender.list.stream()
                            .map(ILoggingEvent::getFormattedMessage)
                            .noneMatch(message ->
                                    message.contains(secret)),
                    "Log output leaked a sensitive value: " + secret
            );
        }
    }
}
