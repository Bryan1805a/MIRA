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


class ConversationApiTests extends ApiIntegrationTestSupport {

    @Test
    void conversationCanBeFoundOnlyByItsOwner() {
        String suffix = UUID.randomUUID().toString();

        UserAccount owner = userAccountRepository.saveAndFlush(
                new UserAccount(
                        "conversation-owner-" + suffix + "@example.com",
                        "test-password-hash"));

        UserAccount otherUser = userAccountRepository.saveAndFlush(
                new UserAccount(
                        "conversation-other-" + suffix + "@example.com",
                        "test-password-hash"));

        Conversation conversation = conversationRepository.saveAndFlush(
                new Conversation(owner, "First conversation"));

        assertTrue(
                conversationRepository.findOwnedById(
                        conversation.getId(),
                        owner.getId()).isPresent());

        assertFalse(
                conversationRepository.findOwnedById(
                        conversation.getId(),
                        otherUser.getId()).isPresent());
    }

    @Test
    void conversationsAreOrderedNewestFirstAndPaginateWithACursor()
            throws InterruptedException {
        UserAccount owner = userAccountRepository.saveAndFlush(
                new UserAccount(
                        "conversation-page-" + UUID.randomUUID()
                                + "@example.com",
                        "test-password-hash"));

        Conversation first = conversationRepository.saveAndFlush(
                new Conversation(owner, "First"));

        Thread.sleep(5);

        Conversation second = conversationRepository.saveAndFlush(
                new Conversation(owner, "Second"));

        Thread.sleep(5);

        Conversation third = conversationRepository.saveAndFlush(
                new Conversation(owner, "Third"));

        List<Conversation> firstPage = conversationRepository.findFirstPage(
                owner.getId(),
                PageRequest.of(0, 2));

        assertEquals(
                List.of(third.getId(), second.getId()),
                firstPage.stream().map(Conversation::getId).toList());

        List<Conversation> secondPage = conversationRepository.findPageAfter(
                owner.getId(),
                second.getUpdatedAt(),
                second.getId(),
                PageRequest.of(0, 2));

        assertEquals(
                List.of(first.getId()),
                secondPage.stream().map(Conversation::getId).toList());
    }

    @Test
    void conversationCanBeCreatedListedAndDeleted() throws Exception {
        String email = "conversation-api-" + UUID.randomUUID()
                + "@example.com";
        String password = "Correct-Horse-123!";

        register(email, password);
        LoginTokens tokens = login(email, password);

        UUID conversationId = createConversation(
                tokens.accessToken(),
                "  Phase 1 conversation  ");

        mockMvc.perform(get("/api/v1/conversations")
                .header(
                        "Authorization",
                        "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id")
                        .value(conversationId.toString()))
                .andExpect(jsonPath("$.items[0].title")
                        .value("Phase 1 conversation"));

        mockMvc.perform(delete(
                "/api/v1/conversations/{id}",
                conversationId)
                .header(
                        "Authorization",
                        "Bearer " + tokens.accessToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/conversations")
                .header(
                        "Authorization",
                        "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void conversationListPaginatesWithAnOpaqueCursor() throws Exception {
        String email = "conversation-page-" + UUID.randomUUID()
                + "@example.com";
        String password = "Correct-Horse-123!";

        register(email, password);
        LoginTokens tokens = login(email, password);

        UUID first = createConversation(tokens.accessToken(), "First");
        Thread.sleep(5);
        UUID second = createConversation(tokens.accessToken(), "Second");
        Thread.sleep(5);
        UUID third = createConversation(tokens.accessToken(), "Third");

        MvcResult firstPageResult = mockMvc.perform(
                get("/api/v1/conversations")
                        .param("limit", "2")
                        .header(
                                "Authorization",
                                "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.nextCursor").isNotEmpty())
                .andReturn();

        var firstPage = objectMapper.readTree(
                firstPageResult.getResponse().getContentAsString());

        String cursor = firstPage.get("nextCursor").stringValue();

        MvcResult secondPageResult = mockMvc.perform(
                get("/api/v1/conversations")
                        .param("limit", "2")
                        .param("cursor", cursor)
                        .header(
                                "Authorization",
                                "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andReturn();

        var secondPage = objectMapper.readTree(
                secondPageResult.getResponse().getContentAsString());

        var ids = new java.util.HashSet<String>();

        firstPage.get("items").forEach(
                item -> ids.add(item.get("id").stringValue()));
        secondPage.get("items").forEach(
                item -> ids.add(item.get("id").stringValue()));

        assertEquals(3, ids.size());
        assertTrue(ids.containsAll(java.util.List.of(
                first.toString(),
                second.toString(),
                third.toString())));
    }

    @Test
    void userCannotDeleteAnotherUsersConversation() throws Exception {
        String password = "Correct-Horse-123!";
        String emailA = "conversation-owner-" + UUID.randomUUID()
                + "@example.com";
        String emailB = "conversation-other-" + UUID.randomUUID()
                + "@example.com";

        register(emailA, password);
        register(emailB, password);

        LoginTokens owner = login(emailA, password);
        LoginTokens other = login(emailB, password);

        UUID conversationId = createConversation(
                owner.accessToken(),
                "Owner only");

        mockMvc.perform(delete(
                "/api/v1/conversations/{id}",
                conversationId)
                .header(
                        "Authorization",
                        "Bearer " + other.accessToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error")
                        .value("resource_not_found"));

        mockMvc.perform(get("/api/v1/conversations")
                .header(
                        "Authorization",
                        "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id")
                        .value(conversationId.toString()));
    }

    @Test
    void conversationListRejectsInvalidPaginationInput() throws Exception {
        String email = "conversation-invalid-" + UUID.randomUUID()
                + "@example.com";
        String password = "Correct-Horse-123!";

        register(email, password);
        LoginTokens tokens = login(email, password);

        mockMvc.perform(get("/api/v1/conversations")
                .param("limit", "51")
                .header(
                        "Authorization",
                        "Bearer " + tokens.accessToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));

        mockMvc.perform(get("/api/v1/conversations")
                .param("cursor", "not-a-cursor")
                .header(
                        "Authorization",
                        "Bearer " + tokens.accessToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));

        mockMvc.perform(delete("/api/v1/conversations/not-a-uuid")
                .header(
                        "Authorization",
                        "Bearer " + tokens.accessToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));
    }

}

