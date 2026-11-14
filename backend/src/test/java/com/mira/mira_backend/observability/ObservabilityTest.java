package com.mira.mira_backend.observability;

import com.mira.mira_backend.ai.MockAIProvider;
import com.mira.mira_backend.dto.LoginRequest;
import com.mira.mira_backend.entity.MemoryExtractionJob;
import com.mira.mira_backend.support.MemoryIntegrationTestSupport;

import io.micrometer.core.instrument.MeterRegistry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ObservabilityTest extends MemoryIntegrationTestSupport {

    @Autowired
    private MeterRegistry registry;

    @BeforeEach
    void resetMetrics() {
        registry.clear();
    }

    @Test
    void successfulChatAndExtractionIncrementCounters()
            throws Exception {
        LoginTokens user = newUser("metrics-chat");
        UUID conversationId = createConversation(
                user.accessToken(),
                "Metrics chat"
        );

        mockAIProvider.setContent("Assistant reply");

        MemoryExtractionJob job = sendAndGetJob(
                user,
                conversationId,
                "Always use a formal tone.",
                "metrics-chat-" + UUID.randomUUID()
        );

        assertEquals(1.0, counter(
                "mira.chat.requests", "outcome", "completed"));

        mockAIProvider.setContent("""
                {
                  "candidates": [
                    {
                      "field": "tone",
                      "value": "formal",
                      "classification": "EXPLICIT_DURABLE",
                      "evidence": "Always use a formal tone."
                    }
                  ]
                }
                """);

        memoryExtractionWorker.processJob(job.getId());

        assertEquals(1.0, counter(
                "mira.extraction.jobs", "status", "COMPLETED"));
    }

    @Test
    void providerFailureAndRetryIncrementCounters() throws Exception {
        LoginTokens user = newUser("metrics-failure");
        UUID conversationId = createConversation(
                user.accessToken(),
                "Metrics failure"
        );

        mockAIProvider.setScenario(MockAIProvider.Scenario.UNAVAILABLE);

        var failed = sendMessage(
                user.accessToken(),
                conversationId,
                "This should fail.",
                "metrics-fail-" + UUID.randomUUID()
        );

        assertEquals(1.0, counter(
                "mira.chat.requests", "outcome", "failed"));
        assertEquals(1.0, counter(
                "mira.chat.provider.errors",
                "category",
                "UNAVAILABLE"));

        mockAIProvider.setScenario(MockAIProvider.Scenario.SUCCESS);
        mockAIProvider.setContent("Recovered reply");

        mockMvc.perform(post(
                "/api/v1/conversations/{cid}/responses/{rid}/retry",
                conversationId,
                responseIdFrom(failed))
                .header(
                        "Authorization",
                        "Bearer " + user.accessToken())
                .header(
                        "Idempotency-Key",
                        "metrics-retry-" + UUID.randomUUID()))
                .andExpect(status().isOk());

        assertEquals(1.0, counter("mira.chat.retries"));
        assertEquals(1.0, counter(
                "mira.chat.requests", "outcome", "completed"));
    }

    @Test
    void rateLimitedLoginIncrementsCounter() throws Exception {
        String email = "metrics-limit-" + UUID.randomUUID()
                + "@example.com";
        String password = "Wrong-Password-123!";

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                            new LoginRequest(email, password))))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new LoginRequest(email, password))))
                .andExpect(status().isTooManyRequests());

        assertEquals(1.0, counter(
                "mira.auth.rate_limited", "scope", "login-email"));
    }

    @Test
    void purgeIncrementsCounter() throws Exception {
        LoginTokens user = newUser("metrics-purge");

        mockMvc.perform(delete("/api/v1/memory/style")
                .header(
                        "Authorization",
                        "Bearer " + user.accessToken()))
                .andExpect(status().isNoContent());

        assertEquals(1.0, counter("mira.memory.purges"));
    }

    private double counter(String name, String... tags) {
        var counter = registry.find(name).tags(tags).counter();

        return counter == null ? 0.0 : counter.count();
    }
}
