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


class MemoryLifecycleTests extends ApiIntegrationTestSupport {

    @Test
    void styleProfileIsProvisionedOnFirstRead() throws Exception {
        String email = "memory-first-" + UUID.randomUUID()
                + "@example.com";
        String password = "Correct-Horse-123!";

        register(email, password);
        LoginTokens tokens = login(email, password);

        mockMvc.perform(get("/api/v1/memory/style")
                .header(
                        "Authorization",
                        "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.profile").isMap())
                .andExpect(jsonPath("$.profile").isEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test
    void styleProfileReplacementUsesOptimisticConcurrency()
            throws Exception {
        String email = "memory-occ-" + UUID.randomUUID()
                + "@example.com";
        String password = "Correct-Horse-123!";

        register(email, password);
        LoginTokens tokens = login(email, password);

        String update = """
                {
                  "expectedVersion": 1,
                  "profile": {
                    "explanation_depth": "detailed",
                    "tone": "formal",
                    "examples": "prefer_concrete_examples",
                    "formatting": "structured",
                    "teaching_sequence": "principles_first"
                  }
                }
                """;

        mockMvc.perform(put("/api/v1/memory/style")
                .header(
                        "Authorization",
                        "Bearer " + tokens.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.profile.explanation_depth")
                        .value("detailed"))
                .andExpect(jsonPath("$.profile.tone").value("formal"));

        mockMvc.perform(put("/api/v1/memory/style")
                .header(
                        "Authorization",
                        "Bearer " + tokens.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(update))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("version_conflict"))
                .andExpect(jsonPath("$.details[0].field")
                        .value("expectedVersion"));
    }

    @Test
    void invalidStyleProfileIsRejected() throws Exception {
        String email = "memory-invalid-" + UUID.randomUUID()
                + "@example.com";
        String password = "Correct-Horse-123!";

        register(email, password);
        LoginTokens tokens = login(email, password);

        String unknownField = """
                {
                  "expectedVersion": 1,
                  "profile": {
                    "favourite_colour": "blue"
                  }
                }
                """;

        mockMvc.perform(put("/api/v1/memory/style")
                .header(
                        "Authorization",
                        "Bearer " + tokens.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(unknownField))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));

        String invalidValue = """
                {
                  "expectedVersion": 1,
                  "profile": {
                    "tone": "sarcastic"
                  }
                }
                """;

        mockMvc.perform(put("/api/v1/memory/style")
                .header(
                        "Authorization",
                        "Bearer " + tokens.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidValue))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));
    }

    @Test
    void memorySettingsCanBeToggled() throws Exception {
        String email = "memory-toggle-" + UUID.randomUUID()
                + "@example.com";
        String password = "Correct-Horse-123!";

        register(email, password);
        LoginTokens tokens = login(email, password);

        mockMvc.perform(patch("/api/v1/memory/style/settings")
                .header(
                        "Authorization",
                        "Bearer " + tokens.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.version").value(2));

        mockMvc.perform(patch("/api/v1/memory/style/settings")
                .header(
                        "Authorization",
                        "Bearer " + tokens.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.version").value(3));
    }

    @Test
    void purgeClearsProfileAndIncrementsGeneration() throws Exception {
        String email = "memory-purge-" + UUID.randomUUID()
                + "@example.com";
        String password = "Correct-Horse-123!";

        register(email, password);
        LoginTokens tokens = login(email, password);

        String update = """
                {
                  "expectedVersion": 1,
                  "profile": {
                    "tone": "formal",
                    "formatting": "prose"
                  }
                }
                """;

        mockMvc.perform(put("/api/v1/memory/style")
                .header(
                        "Authorization",
                        "Bearer " + tokens.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));

        UUID userId = userAccountRepository
                .findByNormalizedEmail(email)
                .orElseThrow()
                .getId();

        int before = memoryControlRepository.findById(userId)
                .orElseThrow()
                .getGeneration();

        mockMvc.perform(delete("/api/v1/memory/style")
                .header(
                        "Authorization",
                        "Bearer " + tokens.accessToken()))
                .andExpect(status().isNoContent());

        int after = memoryControlRepository.findById(userId)
                .orElseThrow()
                .getGeneration();

        assertEquals(before + 1, after);

        mockMvc.perform(get("/api/v1/memory/style")
                .header(
                        "Authorization",
                        "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile").isEmpty())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.version").value(3));
    }

    @Test
    void enabledStyleProfileIsInjectedIntoChatContext() throws Exception {
        String email = "memory-inject-" + UUID.randomUUID()
                + "@example.com";
        String password = "Correct-Horse-123!";

        register(email, password);
        LoginTokens tokens = login(email, password);

        UUID conversationId = createConversation(
                tokens.accessToken(),
                "Memory injection");

        mockMvc.perform(put("/api/v1/memory/style")
                .header(
                        "Authorization",
                        "Bearer " + tokens.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "expectedVersion": 1,
                          "profile": {
                            "explanation_depth": "detailed",
                            "tone": "formal"
                          }
                        }
                        """))
                .andExpect(status().isOk());

        mockAIProvider.setContent("Styled reply");

        MvcResult sendResult = sendMessage(
                tokens.accessToken(),
                conversationId,
                "Hello",
                "memory-send-" + UUID.randomUUID());

        assertEquals(201, sendResult.getResponse().getStatus());

        AIRequest request = mockAIProvider.lastRequest();

        assertNotNull(request);
        assertTrue(requestHasSystemContent(
                request,
                "communication-style preferences"));
        assertTrue(requestHasSystemContent(
                request,
                "explanation_depth: detailed"));
        assertTrue(requestHasSystemContent(
                request,
                "tone: formal"));

        AIMessage lastMessage = request.messages()
                .get(request.messages().size() - 1);

        assertEquals(AIRole.USER, lastMessage.role());
        assertEquals("Hello", lastMessage.content());
    }

    @Test
    void disabledStyleProfileIsOmittedFromChatContext() throws Exception {
        String email = "memory-disabled-" + UUID.randomUUID()
                + "@example.com";
        String password = "Correct-Horse-123!";

        register(email, password);
        LoginTokens tokens = login(email, password);

        UUID conversationId = createConversation(
                tokens.accessToken(),
                "Memory disabled");

        mockMvc.perform(put("/api/v1/memory/style")
                .header(
                        "Authorization",
                        "Bearer " + tokens.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "expectedVersion": 1,
                          "profile": {
                            "tone": "formal"
                          }
                        }
                        """))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/memory/style/settings")
                .header(
                        "Authorization",
                        "Bearer " + tokens.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false}"))
                .andExpect(status().isOk());

        mockAIProvider.setContent("Plain reply");

        sendMessage(
                tokens.accessToken(),
                conversationId,
                "Hello",
                "memory-disabled-send-" + UUID.randomUUID());

        AIRequest request = mockAIProvider.lastRequest();

        assertNotNull(request);
        assertFalse(requestHasSystemContent(
                request,
                "communication-style preferences"));
    }

    @Test
    void emptyStyleProfileDoesNotInjectMemory() throws Exception {
        String email = "memory-empty-" + UUID.randomUUID()
                + "@example.com";
        String password = "Correct-Horse-123!";

        register(email, password);
        LoginTokens tokens = login(email, password);

        UUID conversationId = createConversation(
                tokens.accessToken(),
                "Memory empty");

        mockAIProvider.setContent("Plain reply");

        sendMessage(
                tokens.accessToken(),
                conversationId,
                "Hello",
                "memory-empty-send-" + UUID.randomUUID());

        AIRequest request = mockAIProvider.lastRequest();

        assertNotNull(request);
        assertFalse(requestHasSystemContent(
                request,
                "communication-style preferences"));
    }

    @Test
    void noStrongPreferenceOnlyProfileIsOmittedFromChatContext()
            throws Exception {
        String email = "memory-weak-" + UUID.randomUUID()
                + "@example.com";
        String password = "Correct-Horse-123!";

        register(email, password);
        LoginTokens tokens = login(email, password);

        UUID conversationId = createConversation(
                tokens.accessToken(),
                "Memory weak");

        mockMvc.perform(put("/api/v1/memory/style")
                .header(
                        "Authorization",
                        "Bearer " + tokens.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "expectedVersion": 1,
                          "profile": {
                            "examples": "no_strong_preference",
                            "formatting": "no_strong_preference",
                            "teaching_sequence": "no_strong_preference"
                          }
                        }
                        """))
                .andExpect(status().isOk());

        mockAIProvider.setContent("Plain reply");

        sendMessage(
                tokens.accessToken(),
                conversationId,
                "Hello",
                "memory-weak-send-" + UUID.randomUUID());

        AIRequest request = mockAIProvider.lastRequest();

        assertNotNull(request);
        assertFalse(requestHasSystemContent(
                request,
                "communication-style preferences"));
    }

    @Test
    void completedResponseSchedulesMemoryExtraction() throws Exception {
        String email = "extraction-job-" + UUID.randomUUID()
                + "@example.com";
        String password = "Correct-Horse-123!";

        register(email, password);
        LoginTokens tokens = login(email, password);

        UUID conversationId = createConversation(
                tokens.accessToken(),
                "Extraction job");

        mockAIProvider.setContent("Completed reply");

        MvcResult result = sendMessage(
                tokens.accessToken(),
                conversationId,
                "Hello",
                "extraction-" + UUID.randomUUID());

        assertEquals(201, result.getResponse().getStatus());

        UUID responseId = responseIdFrom(result);

        MemoryExtractionJob job = memoryExtractionJobRepository
                .findByResponseId(responseId)
                .orElseThrow();

        assertEquals(MemoryJobStatus.PENDING, job.getStatus());
        assertEquals(1, job.getExpectedGeneration());
        assertEquals(1, job.getExpectedVersion());
        assertEquals(0, job.getAttempts());
    }

    @Test
    void timeoutResponseDoesNotScheduleMemoryExtraction()
            throws Exception {
        String email = "extraction-timeout-" + UUID.randomUUID()
                + "@example.com";
        String password = "Correct-Horse-123!";

        register(email, password);
        LoginTokens tokens = login(email, password);

        UUID conversationId = createConversation(
                tokens.accessToken(),
                "No extraction on timeout");

        mockAIProvider.setScenario(MockAIProvider.Scenario.TIMEOUT);

        MvcResult result = sendMessage(
                tokens.accessToken(),
                conversationId,
                "Hello",
                "extraction-timeout-" + UUID.randomUUID());

        assertEquals(200, result.getResponse().getStatus());

        UUID responseId = responseIdFrom(result);

        assertTrue(
                memoryExtractionJobRepository
                        .findByResponseId(responseId)
                        .isEmpty());
    }

    @Test
    void disabledMemoryDoesNotScheduleExtraction() throws Exception {
        String email = "extraction-disabled-" + UUID.randomUUID()
                + "@example.com";
        String password = "Correct-Horse-123!";

        register(email, password);
        LoginTokens tokens = login(email, password);

        UUID conversationId = createConversation(
                tokens.accessToken(),
                "Disabled extraction");

        mockMvc.perform(patch("/api/v1/memory/style/settings")
                .header(
                        "Authorization",
                        "Bearer " + tokens.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false}"))
                .andExpect(status().isOk());

        mockAIProvider.setContent("Completed reply");

        MvcResult result = sendMessage(
                tokens.accessToken(),
                conversationId,
                "Hello",
                "extraction-disabled-" + UUID.randomUUID());

        UUID responseId = responseIdFrom(result);

        assertTrue(
                memoryExtractionJobRepository
                        .findByResponseId(responseId)
                        .isEmpty());
    }

    @Test
    void explicitDurableCandidateIsMergedIntoProfile() throws Exception {
        String email = "extract-explicit-" + UUID.randomUUID()
                + "@example.com";
        String password = "Correct-Horse-123!";

        register(email, password);
        LoginTokens tokens = login(email, password);

        UUID conversationId = createConversation(
                tokens.accessToken(),
                "Explicit extraction");

        mockAIProvider.setContent("Assistant reply");

        MvcResult sendResult = sendMessage(
                tokens.accessToken(),
                conversationId,
                "Always use a formal tone.",
                "extract-explicit-" + UUID.randomUUID());

        UUID responseId = responseIdFrom(sendResult);

        MemoryExtractionJob job = memoryExtractionJobRepository
                .findByResponseId(responseId)
                .orElseThrow();

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

        MemoryExtractionJob completed = memoryExtractionJobRepository
                .findById(job.getId())
                .orElseThrow();

        assertEquals(MemoryJobStatus.COMPLETED, completed.getStatus());

        mockMvc.perform(get("/api/v1/memory/style")
                .header(
                        "Authorization",
                        "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.profile.tone").value("formal"));
    }

    @Test
    void inferredCandidateIsStagedWithoutChangingProfile()
            throws Exception {
        String email = "extract-inferred-" + UUID.randomUUID()
                + "@example.com";
        String password = "Correct-Horse-123!";

        register(email, password);
        LoginTokens tokens = login(email, password);

        UUID conversationId = createConversation(
                tokens.accessToken(),
                "Inferred extraction");

        mockAIProvider.setContent("Assistant reply");

        MvcResult sendResult = sendMessage(
                tokens.accessToken(),
                conversationId,
                "I usually prefer academic explanations.",
                "extract-inferred-" + UUID.randomUUID());

        UUID responseId = responseIdFrom(sendResult);

        MemoryExtractionJob job = memoryExtractionJobRepository
                .findByResponseId(responseId)
                .orElseThrow();

        mockAIProvider.setContent("""
                {
                  "candidates": [
                    {
                      "field": "tone",
                      "value": "academic",
                      "classification": "INFERRED",
                      "evidence": "I usually prefer academic explanations."
                    }
                  ]
                }
                """);

        memoryExtractionWorker.processJob(job.getId());

        assertEquals(
                MemoryJobStatus.COMPLETED,
                memoryExtractionJobRepository
                        .findById(job.getId())
                        .orElseThrow()
                        .getStatus());

        mockMvc.perform(get("/api/v1/memory/style")
                .header(
                        "Authorization",
                        "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.profile").isEmpty());

        UUID userId = userAccountRepository
                .findByNormalizedEmail(email)
                .orElseThrow()
                .getId();

        UserMemoryCandidate candidate = userMemoryCandidateRepository
                .findByUserIdAndFieldAndValueAndStatus(
                        userId,
                        "tone",
                        "academic",
                        MemoryCandidateStatus.PENDING)
                .orElseThrow();

        assertEquals(1, candidate.getEvidenceCount());
    }

    @Test
    void staleGenerationSkipsExtraction() throws Exception {
        String email = "extract-stale-" + UUID.randomUUID()
                + "@example.com";
        String password = "Correct-Horse-123!";

        register(email, password);
        LoginTokens tokens = login(email, password);

        UUID conversationId = createConversation(
                tokens.accessToken(),
                "Stale extraction");

        mockAIProvider.setContent("Assistant reply");

        MvcResult sendResult = sendMessage(
                tokens.accessToken(),
                conversationId,
                "Always use a formal tone.",
                "extract-stale-" + UUID.randomUUID());

        UUID responseId = responseIdFrom(sendResult);

        MemoryExtractionJob job = memoryExtractionJobRepository
                .findByResponseId(responseId)
                .orElseThrow();

        mockMvc.perform(delete("/api/v1/memory/style")
                .header(
                        "Authorization",
                        "Bearer " + tokens.accessToken()))
                .andExpect(status().isNoContent());

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

        assertEquals(
                MemoryJobStatus.SKIPPED,
                memoryExtractionJobRepository
                        .findById(job.getId())
                        .orElseThrow()
                        .getStatus());

        mockMvc.perform(get("/api/v1/memory/style")
                .header(
                        "Authorization",
                        "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile").isEmpty());
    }

    @Test
    void malformedExtractionOutputRetriesAndThenFails() throws Exception {
        String email = "extract-invalid-" + UUID.randomUUID()
                + "@example.com";
        String password = "Correct-Horse-123!";

        register(email, password);
        LoginTokens tokens = login(email, password);

        UUID conversationId = createConversation(
                tokens.accessToken(),
                "Invalid extraction"
        );

        mockAIProvider.setContent("Assistant reply");

        MvcResult sendResult = sendMessage(
                tokens.accessToken(),
                conversationId,
                "Hello",
                "extract-invalid-" + UUID.randomUUID()
        );

        UUID responseId = responseIdFrom(sendResult);

        MemoryExtractionJob job = memoryExtractionJobRepository
                .findByResponseId(responseId)
                .orElseThrow();

        mockAIProvider.setContent("This is not JSON.");

        memoryExtractionWorker.processJob(job.getId());

        MemoryExtractionJob firstAttempt = memoryExtractionJobRepository
                .findById(job.getId())
                .orElseThrow();

        assertEquals(MemoryJobStatus.PENDING, firstAttempt.getStatus());
        assertEquals(1, firstAttempt.getAttempts());
        assertEquals("INVALID_OUTPUT", firstAttempt.getErrorCategory());

        memoryExtractionWorker.processJob(job.getId());

        MemoryExtractionJob secondAttempt = memoryExtractionJobRepository
                .findById(job.getId())
                .orElseThrow();

        assertEquals(MemoryJobStatus.PENDING, secondAttempt.getStatus());
        assertEquals(2, secondAttempt.getAttempts());

        memoryExtractionWorker.processJob(job.getId());

        MemoryExtractionJob thirdAttempt = memoryExtractionJobRepository
                .findById(job.getId())
                .orElseThrow();

        assertEquals(MemoryJobStatus.FAILED, thirdAttempt.getStatus());
        assertEquals(3, thirdAttempt.getAttempts());
        assertEquals("INVALID_OUTPUT", thirdAttempt.getErrorCategory());
    }

    @Test
    void pendingCandidateCanBeListedAndAccepted() throws Exception {
        String email = "candidate-accept-" + UUID.randomUUID()
                + "@example.com";
        String password = "Correct-Horse-123!";
    
        register(email, password);
        LoginTokens tokens = login(email, password);
    
        mockMvc.perform(get("/api/v1/memory/style")
                        .header(
                                "Authorization",
                                "Bearer " + tokens.accessToken()
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
                    
        UUID userId = userAccountRepository
                .findByNormalizedEmail(email)
                .orElseThrow()
                .getId();
                    
        int generation = memoryControlRepository
                .findById(userId)
                .orElseThrow()
                .getGeneration();
                    
        UserMemoryCandidate candidate =
                userMemoryCandidateRepository.saveAndFlush(
                        new UserMemoryCandidate(
                                userId,
                                "tone",
                                "formal",
                                MemoryCandidateClassification.INFERRED,
                                "The user seems to prefer a formal tone.",
                                generation
                        )
                );
            
        mockMvc.perform(get("/api/v1/memory/candidates")
                        .header(
                                "Authorization",
                                "Bearer " + tokens.accessToken()
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id")
                        .value(candidate.getId().toString()))
                .andExpect(jsonPath("$.items[0].field").value("tone"))
                .andExpect(jsonPath("$.items[0].value").value("formal"));
                    
        mockMvc.perform(post(
                                "/api/v1/memory/candidates/{id}/accept",
                                candidate.getId()
                        )
                        .header(
                                "Authorization",
                                "Bearer " + tokens.accessToken()
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.profile.tone").value("formal"));
                    
        assertEquals(
                MemoryCandidateStatus.ACCEPTED,
                userMemoryCandidateRepository
                        .findById(candidate.getId())
                        .orElseThrow()
                        .getStatus()
        );
    
        mockMvc.perform(get("/api/v1/memory/candidates")
                        .header(
                                "Authorization",
                                "Bearer " + tokens.accessToken()
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }
    
    @Test
    void candidateCanBeRejectedWithoutChangingProfile() throws Exception {
        String email = "candidate-reject-" + UUID.randomUUID()
                + "@example.com";
        String password = "Correct-Horse-123!";
    
        register(email, password);
        LoginTokens tokens = login(email, password);
    
        mockMvc.perform(get("/api/v1/memory/style")
                        .header(
                                "Authorization",
                                "Bearer " + tokens.accessToken()
                        ))
                .andExpect(status().isOk());
                    
        UUID userId = userAccountRepository
                .findByNormalizedEmail(email)
                .orElseThrow()
                .getId();
                    
        int generation = memoryControlRepository
                .findById(userId)
                .orElseThrow()
                .getGeneration();
                    
        UserMemoryCandidate candidate =
                userMemoryCandidateRepository.saveAndFlush(
                        new UserMemoryCandidate(
                                userId,
                                "tone",
                                "academic",
                                MemoryCandidateClassification.INFERRED,
                                "Possible academic preference.",
                                generation
                        )
                );
            
        mockMvc.perform(post(
                                "/api/v1/memory/candidates/{id}/reject",
                                candidate.getId()
                        )
                        .header(
                                "Authorization",
                                "Bearer " + tokens.accessToken()
                        ))
                .andExpect(status().isNoContent());
                    
        assertEquals(
                MemoryCandidateStatus.REJECTED,
                userMemoryCandidateRepository
                        .findById(candidate.getId())
                        .orElseThrow()
                        .getStatus()
        );
    
        mockMvc.perform(get("/api/v1/memory/style")
                        .header(
                                "Authorization",
                                "Bearer " + tokens.accessToken()
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.profile").isEmpty());
    }
    
    @Test
    void purgeDeletesPendingCandidates() throws Exception {
        String email = "candidate-purge-" + UUID.randomUUID()
                + "@example.com";
        String password = "Correct-Horse-123!";
    
        register(email, password);
        LoginTokens tokens = login(email, password);
    
        mockMvc.perform(get("/api/v1/memory/style")
                        .header(
                                "Authorization",
                                "Bearer " + tokens.accessToken()
                        ))
                .andExpect(status().isOk());
                    
        UUID userId = userAccountRepository
                .findByNormalizedEmail(email)
                .orElseThrow()
                .getId();
                    
        int generation = memoryControlRepository
                .findById(userId)
                .orElseThrow()
                .getGeneration();
                    
        UserMemoryCandidate candidate =
                userMemoryCandidateRepository.saveAndFlush(
                        new UserMemoryCandidate(
                                userId,
                                "formatting",
                                "prose",
                                MemoryCandidateClassification.INFERRED,
                                "Possible prose preference.",
                                generation
                        )
                );
            
        mockMvc.perform(delete("/api/v1/memory/style")
                        .header(
                                "Authorization",
                                "Bearer " + tokens.accessToken()
                        ))
                .andExpect(status().isNoContent());
                    
        assertFalse(
                userMemoryCandidateRepository.existsById(
                        candidate.getId()
                )
        );
    }
    
    @Test
    void userCannotAcceptAnotherUsersCandidate() throws Exception {
        String password = "Correct-Horse-123!";
        String emailA = "candidate-owner-" + UUID.randomUUID()
                + "@example.com";
        String emailB = "candidate-other-" + UUID.randomUUID()
                + "@example.com";
    
        register(emailA, password);
        register(emailB, password);
    
        LoginTokens owner = login(emailA, password);
        LoginTokens other = login(emailB, password);
    
        mockMvc.perform(get("/api/v1/memory/style")
                        .header(
                                "Authorization",
                                "Bearer " + owner.accessToken()
                        ))
                .andExpect(status().isOk());
                    
        UUID ownerId = userAccountRepository
                .findByNormalizedEmail(emailA)
                .orElseThrow()
                .getId();
                    
        int generation = memoryControlRepository
                .findById(ownerId)
                .orElseThrow()
                .getGeneration();
                    
        UserMemoryCandidate candidate =
                userMemoryCandidateRepository.saveAndFlush(
                        new UserMemoryCandidate(
                                ownerId,
                                "tone",
                                "formal",
                                MemoryCandidateClassification.INFERRED,
                                "Owner evidence.",
                                generation
                        )
                );
            
        mockMvc.perform(post(
                                "/api/v1/memory/candidates/{id}/accept",
                                candidate.getId()
                        )
                        .header(
                                "Authorization",
                                "Bearer " + other.accessToken()
                        ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error")
                        .value("resource_not_found"));
    }

    @Test
    void queuedJobsAreSkippedAfterPurge() throws Exception {
        String email = "mem-test-02-" + UUID.randomUUID()
                + "@example.com";
        String password = "Correct-Horse-123!";

        register(email, password);
        LoginTokens tokens = login(email, password);

        UUID conversationId = createConversation(
                tokens.accessToken(),
                "Queued jobs"
        );

        mockAIProvider.setContent("Reply one");
        MemoryExtractionJob first = sendAndGetJob(
                tokens,
                conversationId,
                "Always use a formal tone.",
                "mem-02-a-" + UUID.randomUUID()
        );

        mockAIProvider.setContent("Reply two");
        MemoryExtractionJob second = sendAndGetJob(
                tokens,
                conversationId,
                "Always use a formal tone.",
                "mem-02-b-" + UUID.randomUUID()
        );

        mockMvc.perform(delete("/api/v1/memory/style")
                        .header(
                                "Authorization",
                                "Bearer " + tokens.accessToken()
                        ))
                .andExpect(status().isNoContent());

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

        memoryExtractionWorker.processJob(first.getId());
        memoryExtractionWorker.processJob(second.getId());

        assertEquals(MemoryJobStatus.SKIPPED, jobStatus(first.getId()));
        assertEquals(MemoryJobStatus.SKIPPED, jobStatus(second.getId()));

        mockMvc.perform(get("/api/v1/memory/style")
                        .header(
                                "Authorization",
                                "Bearer " + tokens.accessToken()
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile").isEmpty());
    }

    @Test
    void extractionCommitIsSkippedWhenMemoryIsDisabled()
            throws Exception {
        String email = "mem-test-04-" + UUID.randomUUID()
                + "@example.com";
        String password = "Correct-Horse-123!";

        register(email, password);
        LoginTokens tokens = login(email, password);

        UUID conversationId = createConversation(
                tokens.accessToken(),
                "Disabled commit"
        );

        mockAIProvider.setContent("Reply");
        MemoryExtractionJob job = sendAndGetJob(
                tokens,
                conversationId,
                "Always use a formal tone.",
                "mem-04-" + UUID.randomUUID()
        );

        mockMvc.perform(patch("/api/v1/memory/style/settings")
                        .header(
                                "Authorization",
                                "Bearer " + tokens.accessToken()
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk());

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

        assertEquals(MemoryJobStatus.SKIPPED, jobStatus(job.getId()));

        mockMvc.perform(get("/api/v1/memory/style")
                        .header(
                                "Authorization",
                                "Bearer " + tokens.accessToken()
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.profile").isEmpty());
    }

    @Test
    void newProfileCanBeBuiltAfterPurge() throws Exception {
        String email = "mem-test-05-" + UUID.randomUUID()
                + "@example.com";
        String password = "Correct-Horse-123!";

        register(email, password);
        LoginTokens tokens = login(email, password);

        UUID conversationId = createConversation(
                tokens.accessToken(),
                "Post-deletion profile"
        );

        mockAIProvider.setContent("Reply one");
        MemoryExtractionJob staleJob = sendAndGetJob(
                tokens,
                conversationId,
                "Always use a formal tone.",
                "mem-05-a-" + UUID.randomUUID()
        );

        mockMvc.perform(delete("/api/v1/memory/style")
                        .header(
                                "Authorization",
                                "Bearer " + tokens.accessToken()
                        ))
                .andExpect(status().isNoContent());

        mockAIProvider.setContent("Reply two");
        MemoryExtractionJob freshJob = sendAndGetJob(
                tokens,
                conversationId,
                "Always use a formal tone.",
                "mem-05-b-" + UUID.randomUUID()
        );

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

        memoryExtractionWorker.processJob(freshJob.getId());
        memoryExtractionWorker.processJob(staleJob.getId());

        assertEquals(
                MemoryJobStatus.COMPLETED,
                jobStatus(freshJob.getId())
        );
        assertEquals(
                MemoryJobStatus.SKIPPED,
                jobStatus(staleJob.getId())
        );

        mockMvc.perform(get("/api/v1/memory/style")
                        .header(
                                "Authorization",
                                "Bearer " + tokens.accessToken()
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.profile.tone").value("formal"));
    }

    @Test
    void twoJobsWithTheSameVersionAllowOnlyOneCommit()
            throws Exception {
        String email = "mem-test-06-" + UUID.randomUUID()
                + "@example.com";
        String password = "Correct-Horse-123!";

        register(email, password);
        LoginTokens tokens = login(email, password);

        UUID conversationId = createConversation(
                tokens.accessToken(),
                "OCC collision"
        );

        mockAIProvider.setContent("Reply one");
        MemoryExtractionJob first = sendAndGetJob(
                tokens,
                conversationId,
                "Always use a formal tone.",
                "mem-06-a-" + UUID.randomUUID()
        );

        mockAIProvider.setContent("Reply two");
        MemoryExtractionJob second = sendAndGetJob(
                tokens,
                conversationId,
                "Always use a formal tone.",
                "mem-06-b-" + UUID.randomUUID()
        );

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

        memoryExtractionWorker.processJob(first.getId());
        memoryExtractionWorker.processJob(second.getId());

        assertEquals(MemoryJobStatus.COMPLETED, jobStatus(first.getId()));
        assertEquals(MemoryJobStatus.SKIPPED, jobStatus(second.getId()));

        mockMvc.perform(get("/api/v1/memory/style")
                        .header(
                                "Authorization",
                                "Bearer " + tokens.accessToken()
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.profile.tone").value("formal"));
    }

    @Test
    void staleProcessingJobIsRequeuedAfterRestart() throws Exception {
        String email = "mem-test-07-" + UUID.randomUUID()
                + "@example.com";
        String password = "Correct-Horse-123!";

        register(email, password);
        LoginTokens tokens = login(email, password);

        UUID conversationId = createConversation(
                tokens.accessToken(),
                "Restart recovery"
        );

        mockAIProvider.setContent("Reply");
        MemoryExtractionJob job = sendAndGetJob(
                tokens,
                conversationId,
                "Always use a formal tone.",
                "mem-07-" + UUID.randomUUID()
        );

        memoryExtractionService.claimJob(job.getId());

        jdbcTemplate.update(
                """
                UPDATE memory_extraction_job
                SET started_at = ?
                WHERE id = ?
                """,
                Timestamp.from(Instant.now().minusSeconds(600)),
                job.getId()
        );

        int requeued = memoryExtractionService.requeueStaleProcessing(
                Instant.now().minusSeconds(300),
                3
        );

        assertEquals(1, requeued);
        assertEquals(MemoryJobStatus.PENDING, jobStatus(job.getId()));

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

        assertEquals(MemoryJobStatus.COMPLETED, jobStatus(job.getId()));

        mockMvc.perform(get("/api/v1/memory/style")
                        .header(
                                "Authorization",
                                "Bearer " + tokens.accessToken()
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.tone").value("formal"));
    }

    @Test
    void concurrentPurgeAndExtractionNeverResurrectsProfile()
            throws Exception {
        String email = "mem-test-03-" + UUID.randomUUID()
                + "@example.com";
        String password = "Correct-Horse-123!";

        register(email, password);
        LoginTokens tokens = login(email, password);

        UUID conversationId = createConversation(
                tokens.accessToken(),
                "Concurrent purge"
        );

        mockAIProvider.setContent("Reply");
        MemoryExtractionJob job = sendAndGetJob(
                tokens,
                conversationId,
                "Always use a formal tone.",
                "mem-03-" + UUID.randomUUID()
        );

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

        UUID userId = userIdFor(email);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<?> purgeFuture = executor.submit(() -> {
            start.await();
            styleProfileService.purge(userId);
            return null;
        });

        Future<?> extractionFuture = executor.submit(() -> {
            start.await();
            memoryExtractionWorker.processJob(job.getId());
            return null;
        });

        start.countDown();

        purgeFuture.get(20, TimeUnit.SECONDS);
        extractionFuture.get(20, TimeUnit.SECONDS);

        executor.shutdownNow();

        MemoryJobStatus finalStatus = jobStatus(job.getId());

        assertTrue(
                finalStatus == MemoryJobStatus.COMPLETED
                        || finalStatus == MemoryJobStatus.SKIPPED
        );

        mockMvc.perform(get("/api/v1/memory/style")
                        .header(
                                "Authorization",
                                "Bearer " + tokens.accessToken()
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile").isEmpty());
    }

}

