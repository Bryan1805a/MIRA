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


class PersistenceConstraintTests extends ApiIntegrationTestSupport {

    @Test
    void contextLoads() {
        assertTrue(POSTGRES.isRunning());
    }

    @Test
    void accountCanBeSavedAndFoundByNormalizedEmail() {
        String suffix = UUID.randomUUID().toString()
                .replace("-", "");

        String email = "Bryan" + suffix + "@example.com";

        UserAccount account = userAccountRepository.saveAndFlush(
                new UserAccount(email, "test-password-hash"));

        var found = userAccountRepository.findByNormalizedEmail(
                "  " + email.toUpperCase() + "  ");

        assertTrue(found.isPresent());
        assertEquals(account.getId(), found.get().getId());
    }

    @Test
    void databaseRejectsDuplicateNormalizedEmails() {
        String suffix = UUID.randomUUID().toString()
                .replace("-", "");

        String email = "Bryan" + suffix + "@example.com";
        String duplicateEmail = "  BRYAN" + suffix.toUpperCase() + "@EXAMPLE.COM  ";

        userAccountRepository.saveAndFlush(
                new UserAccount(email, "test-password-hash"));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> userAccountRepository.saveAndFlush(
                        new UserAccount(
                                duplicateEmail,
                                "another-test-password-hash")));
    }

    @Test
    void sessionCanBeSavedAndRetrievedByRefreshTokenHash() {
        UserAccount account = userAccountRepository.saveAndFlush(
                new UserAccount(
                        "session-" + UUID.randomUUID() + "@example.com",
                        "test-password-hash"));

        String tokenHash = newTokenHash();

        UserSession session = userSessionRepository.saveAndFlush(
                new UserSession(
                        account,
                        tokenHash,
                        Instant.now().plusSeconds(3600)));

        var found = userSessionRepository.findByRefreshTokenHash(
                tokenHash);

        assertTrue(found.isPresent());
        assertEquals(session.getId(), found.get().getId());
        assertEquals(account.getId(), found.get().getUser().getId());
    }

    @Test
    void databaseRejectsDuplicateRefreshTokenHashes() {
        UserAccount firstAccount = userAccountRepository.saveAndFlush(
                new UserAccount(
                        "first-" + UUID.randomUUID() + "@example.com",
                        "test-password-hash"));

        UserAccount secondAccount = userAccountRepository.saveAndFlush(
                new UserAccount(
                        "second-" + UUID.randomUUID() + "@example.com",
                        "test-password-hash"));

        String tokenHash = newTokenHash();
        Instant expiry = Instant.now().plusSeconds(3600);

        userSessionRepository.saveAndFlush(
                new UserSession(firstAccount, tokenHash, expiry));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> userSessionRepository.saveAndFlush(
                        new UserSession(
                                secondAccount,
                                tokenHash,
                                expiry)));
    }

    @Test
    void registrationStoresNormalizedEmailAndVerifiablePasswordHash() {
        String email = "  Bryan-" + UUID.randomUUID()
                + "@Example.com  ";
        String password = "Correct-Horse-123!";

        UserAccount account = userAccountService.register(email, password);

        assertEquals(
                email.trim().toLowerCase(java.util.Locale.ROOT),
                account.getEmail());

        assertNotEquals(password, account.getPasswordHash());
        assertTrue(passwordEncoder.matches(
                password,
                account.getPasswordHash()));
    }

    @Test
    void registrationRejectsDuplicateNormalizedEmail() {
        String email = "Bryan-" + UUID.randomUUID() + "@example.com";

        userAccountService.register(email, "Correct-Horse-123!");

        assertThrows(
                DuplicateEmailException.class,
                () -> userAccountService.register(
                        "  " + email.toUpperCase() + "  ",
                        "Another-Password-456!"));
    }

    @Test
    void registrationRejectsInvalidEmailAndWeakPassword() {
        assertThrows(
                InvalidRegistrationException.class,
                () -> userAccountService.register(
                        "not-an-email",
                        "Correct-Horse-123!"));

        assertThrows(
                InvalidRegistrationException.class,
                () -> userAccountService.register(
                        "valid-" + UUID.randomUUID() + "@example.com",
                        "short"));
    }

    @Test
    void aiResponseIdempotencyKeyIsUniquePerUserAndOperation() {
        UserAccount user = userAccountRepository.saveAndFlush(
                new UserAccount(
                        "idempotency-" + UUID.randomUUID() + "@example.com",
                        "test-password-hash"));

        Conversation conversation = conversationRepository.saveAndFlush(
                new Conversation(user, "Idempotency"));

        Message firstMessage = messageRepository.saveAndFlush(
                new Message(conversation, MessageRole.USER, "First"));

        Message secondMessage = messageRepository.saveAndFlush(
                new Message(conversation, MessageRole.USER, "Second"));

        String key = "idem-" + UUID.randomUUID();

        aiResponseRepository.saveAndFlush(
                new AiResponse(
                        user,
                        conversation,
                        firstMessage,
                        "MESSAGE_SEND",
                        key,
                        "a".repeat(64)));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> aiResponseRepository.saveAndFlush(
                        new AiResponse(
                                user,
                                conversation,
                                secondMessage,
                                "MESSAGE_SEND",
                                key,
                                "b".repeat(64))));
    }

}

