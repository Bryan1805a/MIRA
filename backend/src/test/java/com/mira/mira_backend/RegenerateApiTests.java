package com.mira.mira_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.mira.mira_backend.ai.MockAIProvider;
import com.mira.mira_backend.support.ApiIntegrationTestSupport;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

/**
 * Regeneration replaces a completed answer in place, keeping the previous text
 * when the provider fails and never touching another account's conversation.
 */
class RegenerateApiTests extends ApiIntegrationTestSupport {

    private static final String PASSWORD = "Correct-Horse-123!";

    @Test
    void regeneratingReplacesTheCompletedAnswer() throws Exception {
        LoginTokens tokens = newUser("regen");
        UUID conversationId = createConversation(
                tokens.accessToken(),
                "Regenerate"
        );

        mockAIProvider.setScenario(MockAIProvider.Scenario.SUCCESS);
        mockAIProvider.setContent("First answer");

        MvcResult sendResult = sendMessage(
                tokens.accessToken(),
                conversationId,
                "Explain opportunity cost",
                "send-" + UUID.randomUUID()
        );

        UUID responseId = responseIdFrom(sendResult);

        mockAIProvider.setContent("Second answer");

        MvcResult regenerated = regenerateResponse(
                tokens.accessToken(),
                conversationId,
                responseId,
                "regen-" + UUID.randomUUID()
        );

        assertEquals(200, regenerated.getResponse().getStatus());

        var body = objectMapper.readTree(
                regenerated.getResponse().getContentAsString()
        );

        assertTrue(body.get("regenerated").asBoolean());
        assertEquals(
                "Second answer",
                body.get("assistantResponse").get("content").asText()
        );

        MvcResult listed = mockMvc.perform(get(
                        "/api/v1/conversations/{cid}/messages",
                        conversationId)
                        .header(
                                "Authorization",
                                "Bearer " + tokens.accessToken()))
                .andReturn();

        var messages = objectMapper.readTree(
                listed.getResponse().getContentAsString()
        ).get("items");

        assertEquals(2, messages.size());
        assertEquals(
                "Second answer",
                messages.get(1).get("content").asText()
        );

        /* Assistant rows expose their response so regeneration works offline. */
        assertFalse(messages.get(1).get("response").isNull());
        assertEquals(
                responseId.toString(),
                messages.get(1).get("response").get("id").asText()
        );
    }

    @Test
    void regeneratingWithTheSameKeyReturnsTheSameResult() throws Exception {
        LoginTokens tokens = newUser("regen-idem");
        UUID conversationId = createConversation(
                tokens.accessToken(),
                "Regenerate idempotency"
        );

        mockAIProvider.setScenario(MockAIProvider.Scenario.SUCCESS);
        mockAIProvider.setContent("Original");

        UUID responseId = responseIdFrom(sendMessage(
                tokens.accessToken(),
                conversationId,
                "Hello",
                "send-" + UUID.randomUUID()
        ));

        mockAIProvider.setContent("Replacement");
        String key = "regen-" + UUID.randomUUID();

        MvcResult first = regenerateResponse(
                tokens.accessToken(),
                conversationId,
                responseId,
                key
        );

        mockAIProvider.setContent("Should not be used");

        MvcResult second = regenerateResponse(
                tokens.accessToken(),
                conversationId,
                responseId,
                key
        );

        assertEquals(200, first.getResponse().getStatus());
        assertEquals(200, second.getResponse().getStatus());

        var firstBody = objectMapper.readTree(
                first.getResponse().getContentAsString()
        );
        var secondBody = objectMapper.readTree(
                second.getResponse().getContentAsString()
        );

        assertEquals(
                firstBody.get("assistantResponse").get("content").asText(),
                secondBody.get("assistantResponse").get("content").asText()
        );
        assertEquals("Replacement", secondBody
                .get("assistantResponse")
                .get("content")
                .asText());
    }

    @Test
    void regeneratingAFailedResponseIsRejected() throws Exception {
        LoginTokens tokens = newUser("regen-failed");
        UUID conversationId = createConversation(
                tokens.accessToken(),
                "Regenerate failed"
        );

        mockAIProvider.setScenario(MockAIProvider.Scenario.UNAVAILABLE);

        UUID responseId = responseIdFrom(sendMessage(
                tokens.accessToken(),
                conversationId,
                "Hello",
                "send-" + UUID.randomUUID()
        ));

        MvcResult result = regenerateResponse(
                tokens.accessToken(),
                conversationId,
                responseId,
                "regen-" + UUID.randomUUID()
        );

        assertEquals(400, result.getResponse().getStatus());
        assertEquals(
                "invalid_request",
                objectMapper.readTree(
                        result.getResponse().getContentAsString()
                ).get("error").asText()
        );
    }

    @Test
    void providerFailureKeepsThePreviousAnswer() throws Exception {
        LoginTokens tokens = newUser("regen-keep");
        UUID conversationId = createConversation(
                tokens.accessToken(),
                "Regenerate keeps answer"
        );

        mockAIProvider.setScenario(MockAIProvider.Scenario.SUCCESS);
        mockAIProvider.setContent("Keep me");

        UUID responseId = responseIdFrom(sendMessage(
                tokens.accessToken(),
                conversationId,
                "Hello",
                "send-" + UUID.randomUUID()
        ));

        mockAIProvider.setScenario(MockAIProvider.Scenario.UNAVAILABLE);

        MvcResult result = regenerateResponse(
                tokens.accessToken(),
                conversationId,
                responseId,
                "regen-" + UUID.randomUUID()
        );

        assertEquals(200, result.getResponse().getStatus());

        var body = objectMapper.readTree(
                result.getResponse().getContentAsString()
        );

        assertFalse(body.get("regenerated").asBoolean());
        assertEquals(
                "Keep me",
                body.get("assistantResponse").get("content").asText()
        );
    }

    @Test
    void regenerationStopsAtTheAttemptLimit() throws Exception {
        LoginTokens tokens = newUser("regen-limit");
        UUID conversationId = createConversation(
                tokens.accessToken(),
                "Regenerate limit"
        );

        mockAIProvider.setScenario(MockAIProvider.Scenario.SUCCESS);
        mockAIProvider.setContent("Answer");

        UUID responseId = responseIdFrom(sendMessage(
                tokens.accessToken(),
                conversationId,
                "Hello",
                "send-" + UUID.randomUUID()
        ));

        /* One initial attempt plus two regenerations reaches the limit of 3. */
        assertEquals(200, regenerateResponse(
                tokens.accessToken(),
                conversationId,
                responseId,
                "regen-1-" + UUID.randomUUID()
        ).getResponse().getStatus());

        assertEquals(200, regenerateResponse(
                tokens.accessToken(),
                conversationId,
                responseId,
                "regen-2-" + UUID.randomUUID()
        ).getResponse().getStatus());

        MvcResult third = regenerateResponse(
                tokens.accessToken(),
                conversationId,
                responseId,
                "regen-3-" + UUID.randomUUID()
        );

        assertEquals(400, third.getResponse().getStatus());
        assertEquals(
                "invalid_request",
                objectMapper.readTree(
                        third.getResponse().getContentAsString()
                ).get("error").asText()
        );
    }

    @Test
    void userCannotRegenerateAnotherUsersResponse() throws Exception {
        LoginTokens owner = newUser("regen-owner");
        LoginTokens other = newUser("regen-other");

        UUID conversationId = createConversation(
                owner.accessToken(),
                "Regenerate owner"
        );

        mockAIProvider.setScenario(MockAIProvider.Scenario.SUCCESS);
        mockAIProvider.setContent("Owner answer");

        UUID responseId = responseIdFrom(sendMessage(
                owner.accessToken(),
                conversationId,
                "Hello",
                "send-" + UUID.randomUUID()
        ));

        mockMvc.perform(
                        org.springframework.test.web.servlet.request
                                .MockMvcRequestBuilders.post(
                                        "/api/v1/conversations/{cid}/responses/{rid}/regenerate",
                                        conversationId,
                                        responseId)
                                .header(
                                        "Authorization",
                                        "Bearer " + other.accessToken())
                                .header(
                                        "Idempotency-Key",
                                        "regen-" + UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(
                        org.springframework.test.web.servlet.result
                                .MockMvcResultMatchers.status().isNotFound())
                .andExpect(
                        org.springframework.test.web.servlet.result
                                .MockMvcResultMatchers.jsonPath("$.error")
                                .value("resource_not_found"));
    }
}
