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


class AuthApiTests extends ApiIntegrationTestSupport {

    @Test
    void registrationEndpointReturnsCreatedAndDoesNotExposePasswordHash()
            throws Exception {

        var request = new com.mira.mira_backend.dto.RegisterRequest(
                "  Api-" + UUID.randomUUID() + "@Example.com  ",
                "Correct-Horse-123!");

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.email").value(
                        request.email().trim().toLowerCase(
                                java.util.Locale.ROOT)))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void registrationEndpointReturnsConflictForDuplicateEmail()
            throws Exception {

        String email = "api-duplicate-" + UUID.randomUUID()
                + "@example.com";

        var firstRequest = new com.mira.mira_backend.dto.RegisterRequest(
                email,
                "Correct-Horse-123!");

        var duplicateRequest = new com.mira.mira_backend.dto.RegisterRequest(
                "  " + email.toUpperCase() + "  ",
                "Another-Password-456!");

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(firstRequest)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(duplicateRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error")
                        .value("email_already_registered"));
    }

    @Test
    void registrationEndpointRejectsInvalidRequest()
            throws Exception {

        var request = new com.mira.mira_backend.dto.RegisterRequest(
                "not-an-email",
                "short");

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void protectedEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/conversations"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error")
                        .value("authentication_required"));
    }

    @Test
    void logoutRevokesOnlyThePresentedSession() throws Exception {
        String email = "logout-" + UUID.randomUUID() + "@example.com";
        String password = "Correct-Horse-123!";

        register(email, password);
        LoginTokens first = login(email, password);
        LoginTokens second = login(email, password);

        mockMvc.perform(post("/api/v1/auth/logout")
                .header(
                        "Authorization",
                        "Bearer " + first.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new com.mira.mira_backend.dto.LogoutRequest(
                                first.refreshToken()))))
                .andExpect(status().isNoContent());

        assertNotNull(
                userSessionRepository.findById(first.sessionId())
                        .orElseThrow()
                        .getRevokedAt());

        assertNull(
                userSessionRepository.findById(second.sessionId())
                        .orElseThrow()
                        .getRevokedAt());

        // The logged-out session's refresh token must no longer work.
        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new com.mira.mira_backend.dto.RefreshRequest(
                                first.refreshToken()))))
                .andExpect(status().isUnauthorized());

        // The other session must still work.
        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new com.mira.mira_backend.dto.RefreshRequest(
                                second.refreshToken()))))
                .andExpect(status().isOk());
    }

    @Test
    void logoutRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"anything\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error")
                        .value("authentication_required"));
    }

    @Test
    void userCannotRevokeAnotherUsersSession() throws Exception {
        String password = "Correct-Horse-123!";
        String emailA = "logout-a-" + UUID.randomUUID() + "@example.com";
        String emailB = "logout-b-" + UUID.randomUUID() + "@example.com";

        register(emailA, password);
        register(emailB, password);

        LoginTokens a = login(emailA, password);
        LoginTokens b = login(emailB, password);

        mockMvc.perform(post("/api/v1/auth/logout")
                .header(
                        "Authorization",
                        "Bearer " + a.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new com.mira.mira_backend.dto.LogoutRequest(
                                b.refreshToken()))))
                .andExpect(status().isNoContent());

        assertNull(
                userSessionRepository.findById(b.sessionId())
                        .orElseThrow()
                        .getRevokedAt());

        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new com.mira.mira_backend.dto.RefreshRequest(
                                b.refreshToken()))))
                .andExpect(status().isOk());
    }

    @Test
    void logoutRejectsBlankRefreshToken() throws Exception {
        String email = "logout-blank-"
                + UUID.randomUUID() + "@example.com";

        register(email, "Correct-Horse-123!");
        LoginTokens tokens = login(email, "Correct-Horse-123!");

        mockMvc.perform(post("/api/v1/auth/logout")
                .header(
                        "Authorization",
                        "Bearer " + tokens.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void logoutIsIdempotent() throws Exception {
        String email = "logout-idem-"
                + UUID.randomUUID() + "@example.com";

        register(email, "Correct-Horse-123!");
        LoginTokens tokens = login(email, "Correct-Horse-123!");

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/auth/logout")
                    .header(
                            "Authorization",
                            "Bearer " + tokens.accessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                            new com.mira.mira_backend.dto.LogoutRequest(
                                    tokens.refreshToken()))))
                    .andExpect(status().isNoContent());
        }
    }

    @Test
    void meReturnsAuthenticatedUsersProfile() throws Exception {
        String email = "me-" + UUID.randomUUID() + "@example.com";
        String password = "Correct-Horse-123!";

        register(email, password);
        LoginTokens tokens = login(email, password);

        mockMvc.perform(get("/api/v1/me")
                .header(
                        "Authorization",
                        "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void meRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error")
                        .value("authentication_required"));
    }

}

