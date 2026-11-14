package com.mira.mira_backend;

import com.mira.mira_backend.entity.UserAccount;
import com.mira.mira_backend.entity.UserSession;
import com.mira.mira_backend.entity.Conversation;
import com.mira.mira_backend.entity.AiProcessingAttempt;
import com.mira.mira_backend.entity.AiResponse;
import com.mira.mira_backend.entity.AiResponseStatus;
import com.mira.mira_backend.entity.AttemptOutcome;
import com.mira.mira_backend.entity.Message;
import com.mira.mira_backend.entity.MessageRole;
import com.mira.mira_backend.entity.MemoryExtractionJob;
import com.mira.mira_backend.entity.MemoryJobStatus;
import com.mira.mira_backend.entity.MemoryCandidateStatus;
import com.mira.mira_backend.entity.UserMemoryCandidate;
import com.mira.mira_backend.entity.MemoryCandidateClassification;

import com.mira.mira_backend.repository.AiProcessingAttemptRepository;
import com.mira.mira_backend.repository.AiResponseRepository;
import com.mira.mira_backend.repository.MessageRepository;
import com.mira.mira_backend.repository.ConversationRepository;
import com.mira.mira_backend.repository.UserAccountRepository;
import com.mira.mira_backend.repository.UserMemoryControlRepository;
import com.mira.mira_backend.repository.UserSessionRepository;
import com.mira.mira_backend.repository.MemoryExtractionJobRepository;
import com.mira.mira_backend.repository.UserMemoryCandidateRepository;

import com.mira.mira_backend.service.DuplicateEmailException;
import com.mira.mira_backend.service.InvalidRegistrationException;
import com.mira.mira_backend.service.UserAccountService;
import com.mira.mira_backend.service.MemoryExtractionWorker;
import com.mira.mira_backend.service.MemoryExtractionService;
import com.mira.mira_backend.service.StyleProfileService;

import com.mira.mira_backend.ai.MockAIProvider;
import com.mira.mira_backend.ai.AIMessage;
import com.mira.mira_backend.ai.AIRequest;
import com.mira.mira_backend.ai.AIRole;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import tools.jackson.databind.ObjectMapper;


import java.util.UUID;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import java.time.Instant;

import java.sql.Timestamp;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.mira.mira_backend.support.ApiIntegrationTestSupport;

import static org.junit.jupiter.api.Assertions.*;


class MessagingApiTests extends ApiIntegrationTestSupport {

    @Test
    void messagingRowsPersistThroughTheResponseLifecycle() {
        UserAccount user = userAccountRepository.saveAndFlush(
                new UserAccount(
                        "message-" + UUID.randomUUID() + "@example.com",
                        "test-password-hash"));

        Conversation conversation = conversationRepository.saveAndFlush(
                new Conversation(user, "Messaging"));

        Message userMessage = messageRepository.saveAndFlush(
                new Message(
                        conversation,
                        MessageRole.USER,
                        "Hello"));

        AiResponse response = aiResponseRepository.saveAndFlush(
                new AiResponse(
                        user,
                        conversation,
                        userMessage,
                        "MESSAGE_SEND",
                        "idem-" + UUID.randomUUID(),
                        "a".repeat(64)));

        assertEquals(AiResponseStatus.PENDING, response.getStatus());

        AiProcessingAttempt attempt = aiProcessingAttemptRepository.saveAndFlush(
                new AiProcessingAttempt(response, 1));

        assertEquals(AttemptOutcome.IN_PROGRESS, attempt.getOutcome());
        assertNull(attempt.getFinishedAt());

        Message assistantMessage = messageRepository.saveAndFlush(
                new Message(
                        conversation,
                        MessageRole.ASSISTANT,
                        "Hi there"));

        response.markCompleted(assistantMessage);
        attempt.succeed();

        aiResponseRepository.saveAndFlush(response);
        aiProcessingAttemptRepository.saveAndFlush(attempt);

        assertEquals(AiResponseStatus.COMPLETED, response.getStatus());
        assertEquals(
                assistantMessage.getId(),
                response.getAssistantMessage().getId());
        assertEquals(AttemptOutcome.SUCCESS, attempt.getOutcome());
        assertNotNull(attempt.getFinishedAt());
    }

    @Test
    void sendMessagePersistsAndCompletesResponse() throws Exception {
        String email = "send-" + UUID.randomUUID() + "@example.com";
        String password = "Correct-Horse-123!";

        register(email, password);
        LoginTokens tokens = login(email, password);
        UUID conversationId = createConversation(
                tokens.accessToken(),
                "Send test");

        mockAIProvider.setContent("Hello from MIRA");

        MvcResult result = sendMessage(
                tokens.accessToken(),
                conversationId,
                "Hello",
                "send-" + UUID.randomUUID());

        assertEquals(201, result.getResponse().getStatus());

        var body = objectMapper.readTree(
                result.getResponse().getContentAsString());

        assertEquals(
                "COMPLETED",
                body.get("assistantResponse")
                        .get("status")
                        .stringValue());
        assertEquals(
                "Hello from MIRA",
                body.get("assistantResponse")
                        .get("content")
                        .stringValue());

        UUID responseId = UUID.fromString(
                body.get("assistantResponse")
                        .get("id")
                        .stringValue());

        mockMvc.perform(get(
                "/api/v1/conversations/{id}/messages",
                conversationId)
                .header(
                        "Authorization",
                        "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].role").value("USER"))
                .andExpect(jsonPath("$.items[0].response.id")
                        .value(responseId.toString()))
                .andExpect(jsonPath("$.items[0].response.status")
                        .value("COMPLETED"))
                .andExpect(jsonPath("$.items[1].role")
                        .value("ASSISTANT"))
                .andExpect(jsonPath("$.items[1].content")
                        .value("Hello from MIRA"));
    }

    @Test
    void sendMessageIsIdempotentForTheSameKeyAndPayload() throws Exception {
        String email = "send-idem-" + UUID.randomUUID() + "@example.com";
        String password = "Correct-Horse-123!";

        register(email, password);
        LoginTokens tokens = login(email, password);
        UUID conversationId = createConversation(
                tokens.accessToken(),
                "Idempotent send");

        String key = "send-" + UUID.randomUUID();

        MvcResult first = sendMessage(
                tokens.accessToken(),
                conversationId,
                "Hello",
                key);

        MvcResult second = sendMessage(
                tokens.accessToken(),
                conversationId,
                "Hello",
                key);

        assertEquals(201, first.getResponse().getStatus());
        assertEquals(201, second.getResponse().getStatus());

        var firstBody = objectMapper.readTree(
                first.getResponse().getContentAsString());
        var secondBody = objectMapper.readTree(
                second.getResponse().getContentAsString());

        assertEquals(
                firstBody.get("userMessage").get("id").stringValue(),
                secondBody.get("userMessage").get("id").stringValue());
        assertEquals(
                firstBody.get("assistantResponse").get("id").stringValue(),
                secondBody.get("assistantResponse").get("id").stringValue());

        mockMvc.perform(get(
                "/api/v1/conversations/{id}/messages",
                conversationId)
                .header(
                        "Authorization",
                        "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    void sendMessageRejectsAReusedKeyWithDifferentContent() throws Exception {
        String email = "send-conflict-" + UUID.randomUUID() + "@example.com";
        String password = "Correct-Horse-123!";

        register(email, password);
        LoginTokens tokens = login(email, password);
        UUID conversationId = createConversation(
                tokens.accessToken(),
                "Conflict test");

        String key = "send-" + UUID.randomUUID();

        sendMessage(
                tokens.accessToken(),
                conversationId,
                "First content",
                key);

        mockMvc.perform(post(
                "/api/v1/conversations/{id}/messages",
                conversationId)
                .header(
                        "Authorization",
                        "Bearer " + tokens.accessToken())
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new com.mira.mira_backend.dto.SendMessageRequest(
                                "Different content"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error")
                        .value("idempotency_conflict"));
    }

    @Test
    void providerTimeoutIsPersistedAsUnknown() throws Exception {
        String email = "send-timeout-" + UUID.randomUUID() + "@example.com";
        String password = "Correct-Horse-123!";

        register(email, password);
        LoginTokens tokens = login(email, password);
        UUID conversationId = createConversation(
                tokens.accessToken(),
                "Timeout test");

        mockAIProvider.setScenario(MockAIProvider.Scenario.TIMEOUT);

        MvcResult result = sendMessage(
                tokens.accessToken(),
                conversationId,
                "Hello",
                "timeout-" + UUID.randomUUID());

        assertEquals(200, result.getResponse().getStatus());

        var body = objectMapper.readTree(
                result.getResponse().getContentAsString());

        assertEquals(
                "UNKNOWN",
                body.get("assistantResponse")
                        .get("status")
                        .stringValue());

        mockMvc.perform(get(
                "/api/v1/conversations/{id}/messages",
                conversationId)
                .header(
                        "Authorization",
                        "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].role").value("USER"))
                .andExpect(jsonPath("$.items[0].response.status")
                        .value("UNKNOWN"));
    }

    @Test
    void userCannotSendToAnotherUsersConversation() throws Exception {
        String password = "Correct-Horse-123!";
        String emailA = "send-owner-" + UUID.randomUUID() + "@example.com";
        String emailB = "send-other-" + UUID.randomUUID() + "@example.com";

        register(emailA, password);
        register(emailB, password);

        LoginTokens owner = login(emailA, password);
        LoginTokens other = login(emailB, password);

        UUID conversationId = createConversation(
                owner.accessToken(),
                "Owner only");

        MvcResult result = sendMessage(
                other.accessToken(),
                conversationId,
                "Intrusion attempt",
                "send-" + UUID.randomUUID());

        assertEquals(404, result.getResponse().getStatus());
    }

    @Test
    void sendMessageRequiresAnIdempotencyKey() throws Exception {
        String email = "send-no-key-" + UUID.randomUUID() + "@example.com";
        String password = "Correct-Horse-123!";

        register(email, password);
        LoginTokens tokens = login(email, password);
        UUID conversationId = createConversation(
                tokens.accessToken(),
                "No key");

        mockMvc.perform(post(
                "/api/v1/conversations/{id}/messages",
                conversationId)
                .header(
                        "Authorization",
                        "Bearer " + tokens.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new com.mira.mira_backend.dto.SendMessageRequest(
                                "Hello"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));
    }

    @Test
    void retryCompletesAnUnknownResponse() throws Exception {
        String email = "retry-" + UUID.randomUUID() + "@example.com";
        String password = "Correct-Horse-123!";

        register(email, password);
        LoginTokens tokens = login(email, password);
        UUID conversationId = createConversation(
                tokens.accessToken(),
                "Retry test");

        mockAIProvider.setScenario(MockAIProvider.Scenario.TIMEOUT);

        MvcResult sendResult = sendMessage(
                tokens.accessToken(),
                conversationId,
                "Hello",
                "send-" + UUID.randomUUID());

        assertEquals(200, sendResult.getResponse().getStatus());

        UUID responseId = responseIdFrom(sendResult);

        mockAIProvider.setScenario(MockAIProvider.Scenario.SUCCESS);
        mockAIProvider.setContent("Recovered response");

        MvcResult retryResult = retryResponse(
                tokens.accessToken(),
                conversationId,
                responseId,
                "retry-" + UUID.randomUUID());

        assertEquals(200, retryResult.getResponse().getStatus());

        var body = objectMapper.readTree(
                retryResult.getResponse().getContentAsString());

        assertEquals(
                responseId.toString(),
                body.get("assistantResponse")
                        .get("id")
                        .stringValue());
        assertEquals(
                "COMPLETED",
                body.get("assistantResponse")
                        .get("status")
                        .stringValue());
        assertEquals(
                "Recovered response",
                body.get("assistantResponse")
                        .get("content")
                        .stringValue());

        mockMvc.perform(get(
                "/api/v1/conversations/{id}/messages",
                conversationId)
                .header(
                        "Authorization",
                        "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].role").value("USER"))
                .andExpect(jsonPath("$.items[0].response.status")
                        .value("COMPLETED"))
                .andExpect(jsonPath("$.items[1].role")
                        .value("ASSISTANT"))
                .andExpect(jsonPath("$.items[1].content")
                        .value("Recovered response"));
    }

    @Test
    void retryWithTheSameKeyIsIdempotent() throws Exception {
        String email = "retry-idem-" + UUID.randomUUID() + "@example.com";
        String password = "Correct-Horse-123!";

        register(email, password);
        LoginTokens tokens = login(email, password);
        UUID conversationId = createConversation(
                tokens.accessToken(),
                "Retry idempotency");

        mockAIProvider.setScenario(MockAIProvider.Scenario.TIMEOUT);

        MvcResult sendResult = sendMessage(
                tokens.accessToken(),
                conversationId,
                "Hello",
                "send-" + UUID.randomUUID());

        UUID responseId = responseIdFrom(sendResult);

        mockAIProvider.setScenario(MockAIProvider.Scenario.SUCCESS);
        mockAIProvider.setContent("Only once");

        String retryKey = "retry-" + UUID.randomUUID();

        MvcResult first = retryResponse(
                tokens.accessToken(),
                conversationId,
                responseId,
                retryKey);

        MvcResult second = retryResponse(
                tokens.accessToken(),
                conversationId,
                responseId,
                retryKey);

        assertEquals(200, first.getResponse().getStatus());
        assertEquals(200, second.getResponse().getStatus());

        var firstBody = objectMapper.readTree(
                first.getResponse().getContentAsString());
        var secondBody = objectMapper.readTree(
                second.getResponse().getContentAsString());

        assertEquals(
                firstBody.get("assistantResponse").get("id").stringValue(),
                secondBody.get("assistantResponse").get("id").stringValue());

        mockMvc.perform(get(
                "/api/v1/conversations/{id}/messages",
                conversationId)
                .header(
                        "Authorization",
                        "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    void retryLimitIsEnforced() throws Exception {
        String email = "retry-limit-" + UUID.randomUUID() + "@example.com";
        String password = "Correct-Horse-123!";

        register(email, password);
        LoginTokens tokens = login(email, password);
        UUID conversationId = createConversation(
                tokens.accessToken(),
                "Retry limit");

        mockAIProvider.setScenario(MockAIProvider.Scenario.TIMEOUT);

        MvcResult sendResult = sendMessage(
                tokens.accessToken(),
                conversationId,
                "Hello",
                "send-" + UUID.randomUUID());

        UUID responseId = responseIdFrom(sendResult);

        // Attempt 2
        assertEquals(
                200,
                retryResponse(
                        tokens.accessToken(),
                        conversationId,
                        responseId,
                        "retry-2-" + UUID.randomUUID()).getResponse().getStatus());

        // Attempt 3
        assertEquals(
                200,
                retryResponse(
                        tokens.accessToken(),
                        conversationId,
                        responseId,
                        "retry-3-" + UUID.randomUUID()).getResponse().getStatus());

        // Attempt 4 must be rejected
        mockMvc.perform(post(
                "/api/v1/conversations/{cid}/responses/{rid}/retry",
                conversationId,
                responseId)
                .header(
                        "Authorization",
                        "Bearer " + tokens.accessToken())
                .header(
                        "Idempotency-Key",
                        "retry-4-" + UUID.randomUUID()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));
    }

    @Test
    void retryRejectsACompletedResponse() throws Exception {
        String email = "retry-completed-" + UUID.randomUUID()
                + "@example.com";
        String password = "Correct-Horse-123!";

        register(email, password);
        LoginTokens tokens = login(email, password);
        UUID conversationId = createConversation(
                tokens.accessToken(),
                "Completed retry");

        mockAIProvider.setScenario(MockAIProvider.Scenario.SUCCESS);
        mockAIProvider.setContent("Done");

        MvcResult sendResult = sendMessage(
                tokens.accessToken(),
                conversationId,
                "Hello",
                "send-" + UUID.randomUUID());

        UUID responseId = responseIdFrom(sendResult);

        mockMvc.perform(post(
                "/api/v1/conversations/{cid}/responses/{rid}/retry",
                conversationId,
                responseId)
                .header(
                        "Authorization",
                        "Bearer " + tokens.accessToken())
                .header(
                        "Idempotency-Key",
                        "retry-" + UUID.randomUUID()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));
    }

    @Test
    void userCannotRetryAnotherUsersResponse() throws Exception {
        String password = "Correct-Horse-123!";
        String emailA = "retry-owner-" + UUID.randomUUID()
                + "@example.com";
        String emailB = "retry-other-" + UUID.randomUUID()
                + "@example.com";

        register(emailA, password);
        register(emailB, password);

        LoginTokens owner = login(emailA, password);
        LoginTokens other = login(emailB, password);

        UUID conversationId = createConversation(
                owner.accessToken(),
                "Retry owner");

        mockAIProvider.setScenario(MockAIProvider.Scenario.TIMEOUT);

        MvcResult sendResult = sendMessage(
                owner.accessToken(),
                conversationId,
                "Hello",
                "send-" + UUID.randomUUID());

        UUID responseId = responseIdFrom(sendResult);

        mockMvc.perform(post(
                "/api/v1/conversations/{cid}/responses/{rid}/retry",
                conversationId,
                responseId)
                .header(
                        "Authorization",
                        "Bearer " + other.accessToken())
                .header(
                        "Idempotency-Key",
                        "retry-" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error")
                        .value("resource_not_found"));
    }

    @Test
    void messageRateLimitReturnsTooManyRequests() throws Exception {
        String email = "rate-limit-" + UUID.randomUUID()
                + "@example.com";
        String password = "Correct-Horse-123!";

        register(email, password);
        LoginTokens tokens = login(email, password);

        UUID conversationId = createConversation(
                tokens.accessToken(),
                "Rate limit test");

        for (int index = 0; index < 10; index++) {
            MvcResult result = sendMessage(
                    tokens.accessToken(),
                    conversationId,
                    "Message " + index,
                    "rate-" + index + "-" + UUID.randomUUID());

            assertEquals(201, result.getResponse().getStatus());
        }

        mockMvc.perform(post(
                "/api/v1/conversations/{id}/messages",
                conversationId)
                .header(
                        "Authorization",
                        "Bearer " + tokens.accessToken())
                .header(
                        "Idempotency-Key",
                        "rate-final-" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new com.mira.mira_backend.dto.SendMessageRequest(
                                "One too many"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("rate_limited"));
    }

}

