package com.mira.mira_backend;

import com.mira.mira_backend.support.ApiIntegrationTestSupport;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LatencySmokeTest extends ApiIntegrationTestSupport {

    private static final int SAMPLES = 20;
    private static final double P95_BUDGET_MS = 200.0;

    @Test
    void localReadEndpointsStayWithinTheLatencyBudget() throws Exception {
        LoginTokens user = newUser("latency");
        UUID conversationId = createConversation(
                user.accessToken(),
                "Latency smoke"
        );

        // Warm up JPA, connection pool, and serialization.
        performReads(user, conversationId);

        List<Long> samples = new ArrayList<>(SAMPLES);

        for (int index = 0; index < SAMPLES; index++) {
            long startedAt = System.nanoTime();

            performReads(user, conversationId);

            samples.add(System.nanoTime() - startedAt);
        }

        samples.sort(Long::compareTo);

        int p95Index = (int) Math.ceil(samples.size() * 0.95) - 1;
        double p95Milliseconds =
                samples.get(p95Index) / 1_000_000.0;

        assertTrue(
                p95Milliseconds < P95_BUDGET_MS,
                "Local read p95 was "
                        + String.format("%.2f", p95Milliseconds)
                        + " ms; budget is "
                        + P95_BUDGET_MS
                        + " ms"
        );
    }

    private void performReads(
            LoginTokens user,
            UUID conversationId) throws Exception {
        mockMvc.perform(get("/api/v1/conversations")
                .header(
                        "Authorization",
                        "Bearer " + user.accessToken()))
                .andExpect(status().isOk());

        mockMvc.perform(get(
                "/api/v1/conversations/{id}/messages",
                conversationId)
                .header(
                        "Authorization",
                        "Bearer " + user.accessToken()))
                .andExpect(status().isOk());
    }
}
