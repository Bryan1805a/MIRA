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

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class MiraBackendApplicationTests {

    @SuppressWarnings("resource")
    @Container
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                    DockerImageName.parse("postgres:18-alpine"))
                    .withDatabaseName("mira_test")
                    .withUsername("mira_test")
                    .withPassword("mira_test");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name",
                postgres::getDriverClassName);
    }

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private UserSessionRepository userSessionRepository;

    @Autowired
    private UserAccountService userAccountService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRepository messageRepository;
    
    @Autowired
    private AiResponseRepository aiResponseRepository;
    
    @Autowired
    private AiProcessingAttemptRepository aiProcessingAttemptRepository;

    @Autowired
    private MockAIProvider mockAIProvider;

    @Autowired
    private UserMemoryControlRepository memoryControlRepository;

    @Autowired
    private MemoryExtractionJobRepository memoryExtractionJobRepository;

    @Autowired
    private MemoryExtractionWorker memoryExtractionWorker;
    
    @Autowired
    private UserMemoryCandidateRepository userMemoryCandidateRepository;

    @BeforeEach
    void resetMockProvider() {
        mockAIProvider.reset();
    }

    @Test
    void contextLoads() {
        assertTrue(postgres.isRunning());
    }

    @Test
    void accountCanBeSavedAndFoundByNormalizedEmail() {
        String suffix = UUID.randomUUID().toString()
                .replace("-", "");

        String email = "Bryan" + suffix + "@example.com";

        UserAccount account = userAccountRepository.saveAndFlush(
                new UserAccount(email, "test-password-hash")
        );

        var found = userAccountRepository.findByNormalizedEmail(
                "  " + email.toUpperCase() + "  "
        );

        assertTrue(found.isPresent());
        assertEquals(account.getId(), found.get().getId());
    }

    @Test
    void databaseRejectsDuplicateNormalizedEmails() {
        String suffix = UUID.randomUUID().toString()
                .replace("-", "");

        String email = "Bryan" + suffix + "@example.com";
        String duplicateEmail =
                "  BRYAN" + suffix.toUpperCase() + "@EXAMPLE.COM  ";

        userAccountRepository.saveAndFlush(
                new UserAccount(email, "test-password-hash")
        );

        assertThrows(
                DataIntegrityViolationException.class,
                () -> userAccountRepository.saveAndFlush(
                        new UserAccount(
                                duplicateEmail,
                                "another-test-password-hash"
                        )
                )
        );
    }

    @Test
    void sessionCanBeSavedAndRetrievedByRefreshTokenHash() {
        UserAccount account = userAccountRepository.saveAndFlush(
                new UserAccount(
                        "session-" + UUID.randomUUID() + "@example.com",
                        "test-password-hash"
                )
        );

        String tokenHash = newTokenHash();

        UserSession session = userSessionRepository.saveAndFlush(
                new UserSession(
                        account,
                        tokenHash,
                        Instant.now().plusSeconds(3600)
                )
        );

        var found = userSessionRepository.findByRefreshTokenHash(
                tokenHash
        );

        assertTrue(found.isPresent());
        assertEquals(session.getId(), found.get().getId());
        assertEquals(account.getId(), found.get().getUser().getId());
    }

    @Test
    void databaseRejectsDuplicateRefreshTokenHashes() {
        UserAccount firstAccount = userAccountRepository.saveAndFlush(
                new UserAccount(
                        "first-" + UUID.randomUUID() + "@example.com",
                        "test-password-hash"
                )
        );

        UserAccount secondAccount = userAccountRepository.saveAndFlush(
                new UserAccount(
                        "second-" + UUID.randomUUID() + "@example.com",
                        "test-password-hash"
                )
        );

        String tokenHash = newTokenHash();
        Instant expiry = Instant.now().plusSeconds(3600);

        userSessionRepository.saveAndFlush(
                new UserSession(firstAccount, tokenHash, expiry)
        );

        assertThrows(
                DataIntegrityViolationException.class,
                () -> userSessionRepository.saveAndFlush(
                        new UserSession(
                                secondAccount,
                                tokenHash,
                                expiry
                        )
                )
        );
    }

    @Test
    void registrationStoresNormalizedEmailAndVerifiablePasswordHash() {
        String email = "  Bryan-" + UUID.randomUUID()
                + "@Example.com  ";
        String password = "Correct-Horse-123!";
    
        UserAccount account =
                userAccountService.register(email, password);
    
        assertEquals(
                email.trim().toLowerCase(java.util.Locale.ROOT),
                account.getEmail()
        );
    
        assertNotEquals(password, account.getPasswordHash());
        assertTrue(passwordEncoder.matches(
                password,
                account.getPasswordHash()
        ));
    }
    
    @Test
    void registrationRejectsDuplicateNormalizedEmail() {
        String email = "Bryan-" + UUID.randomUUID() + "@example.com";
    
        userAccountService.register(email, "Correct-Horse-123!");
    
        assertThrows(
                DuplicateEmailException.class,
                () -> userAccountService.register(
                        "  " + email.toUpperCase() + "  ",
                        "Another-Password-456!"
                )
        );
    }
    
    @Test
    void registrationRejectsInvalidEmailAndWeakPassword() {
        assertThrows(
                InvalidRegistrationException.class,
                () -> userAccountService.register(
                        "not-an-email",
                        "Correct-Horse-123!"
                )
        );
    
        assertThrows(
                InvalidRegistrationException.class,
                () -> userAccountService.register(
                        "valid-" + UUID.randomUUID() + "@example.com",
                        "short"
                )
        );
    }


    @Test
    void registrationEndpointReturnsCreatedAndDoesNotExposePasswordHash()
            throws Exception {
            
        var request = new com.mira.mira_backend.dto.RegisterRequest(
                "  Api-" + UUID.randomUUID() + "@Example.com  ",
                "Correct-Horse-123!"
        );
    
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.email").value(
                        request.email().trim().toLowerCase(
                                java.util.Locale.ROOT
                        )
                ))
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
                "Correct-Horse-123!"
        );
    
        var duplicateRequest = new com.mira.mira_backend.dto.RegisterRequest(
                "  " + email.toUpperCase() + "  ",
                "Another-Password-456!"
        );
    
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
                "short"
        );
    
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
                                "Bearer " + first.accessToken()
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.mira.mira_backend.dto.LogoutRequest(
                                        first.refreshToken()
                                )
                        )))
                .andExpect(status().isNoContent());
    
        assertNotNull(
                userSessionRepository.findById(first.sessionId())
                        .orElseThrow()
                        .getRevokedAt()
        );
    
        assertNull(
                userSessionRepository.findById(second.sessionId())
                        .orElseThrow()
                        .getRevokedAt()
        );
    
        // The logged-out session's refresh token must no longer work.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.mira.mira_backend.dto.RefreshRequest(
                                        first.refreshToken()
                                )
                        )))
                .andExpect(status().isUnauthorized());
    
        // The other session must still work.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.mira.mira_backend.dto.RefreshRequest(
                                        second.refreshToken()
                                )
                        )))
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
                                "Bearer " + a.accessToken()
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.mira.mira_backend.dto.LogoutRequest(
                                        b.refreshToken()
                                )
                        )))
                .andExpect(status().isNoContent());
    
        assertNull(
                userSessionRepository.findById(b.sessionId())
                        .orElseThrow()
                        .getRevokedAt()
        );
    
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.mira.mira_backend.dto.RefreshRequest(
                                        b.refreshToken()
                                )
                        )))
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
                                "Bearer " + tokens.accessToken()
                        )
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
                                    "Bearer " + tokens.accessToken()
                            )
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new com.mira.mira_backend.dto.LogoutRequest(
                                            tokens.refreshToken()
                                    )
                            )))
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
                                "Bearer " + tokens.accessToken()
                        ))
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

    @Test
    void conversationCanBeFoundOnlyByItsOwner() {
        String suffix = UUID.randomUUID().toString();
    
        UserAccount owner = userAccountRepository.saveAndFlush(
                new UserAccount(
                        "conversation-owner-" + suffix + "@example.com",
                        "test-password-hash"
                )
        );
    
        UserAccount otherUser = userAccountRepository.saveAndFlush(
                new UserAccount(
                        "conversation-other-" + suffix + "@example.com",
                        "test-password-hash"
                )
        );
    
        Conversation conversation = conversationRepository.saveAndFlush(
                new Conversation(owner, "First conversation")
        );
    
        assertTrue(
                conversationRepository.findOwnedById(
                        conversation.getId(),
                        owner.getId()
                ).isPresent()
        );
    
        assertFalse(
                conversationRepository.findOwnedById(
                        conversation.getId(),
                        otherUser.getId()
                ).isPresent()
        );
    }

    @Test
    void conversationsAreOrderedNewestFirstAndPaginateWithACursor()
            throws InterruptedException {
        UserAccount owner = userAccountRepository.saveAndFlush(
                new UserAccount(
                        "conversation-page-" + UUID.randomUUID()
                                + "@example.com",
                        "test-password-hash"
                )
        );
    
        Conversation first = conversationRepository.saveAndFlush(
                new Conversation(owner, "First")
        );
    
        Thread.sleep(5);
    
        Conversation second = conversationRepository.saveAndFlush(
                new Conversation(owner, "Second")
        );
    
        Thread.sleep(5);
    
        Conversation third = conversationRepository.saveAndFlush(
                new Conversation(owner, "Third")
        );
    
        List<Conversation> firstPage =
                conversationRepository.findFirstPage(
                        owner.getId(),
                        PageRequest.of(0, 2)
                );
    
        assertEquals(
                List.of(third.getId(), second.getId()),
                firstPage.stream().map(Conversation::getId).toList()
        );
    
        List<Conversation> secondPage =
                conversationRepository.findPageAfter(
                        owner.getId(),
                        second.getUpdatedAt(),
                        second.getId(),
                        PageRequest.of(0, 2)
                );
    
        assertEquals(
                List.of(first.getId()),
                secondPage.stream().map(Conversation::getId).toList()
        );
    }

    @Test
    void errorResponsesExposeRequestIdHeaderAndBody() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.error")
                        .value("authentication_required"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }
    
    @Test
    void suppliedRequestIdIsEchoed() throws Exception {
        String requestId = "test-request-123";
    
        mockMvc.perform(get("/api/v1/me")
                        .header("X-Request-Id", requestId))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Request-Id", requestId))
                .andExpect(jsonPath("$.requestId").value(requestId));
    }
    
    @Test
    void validationErrorsIncludeFieldDetails() throws Exception {
        var request = new com.mira.mira_backend.dto.RegisterRequest(
                "valid-" + UUID.randomUUID() + "@example.com",
                "short"
        );
    
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"))
                .andExpect(jsonPath("$.details[0].field").value("password"))
                .andExpect(jsonPath("$.details[0].reason").isNotEmpty());
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
                "  Phase 1 conversation  "
        );
    
        mockMvc.perform(get("/api/v1/conversations")
                        .header(
                                "Authorization",
                                "Bearer " + tokens.accessToken()
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id")
                        .value(conversationId.toString()))
                .andExpect(jsonPath("$.items[0].title")
                        .value("Phase 1 conversation"));
    
        mockMvc.perform(delete(
                                "/api/v1/conversations/{id}",
                                conversationId
                        )
                        .header(
                                "Authorization",
                                "Bearer " + tokens.accessToken()
                        ))
                .andExpect(status().isNoContent());
    
        mockMvc.perform(get("/api/v1/conversations")
                        .header(
                                "Authorization",
                                "Bearer " + tokens.accessToken()
                        ))
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
                                        "Bearer " + tokens.accessToken()
                                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.nextCursor").isNotEmpty())
                .andReturn();
    
        var firstPage = objectMapper.readTree(
                firstPageResult.getResponse().getContentAsString()
        );
    
        String cursor = firstPage.get("nextCursor").stringValue();
    
        MvcResult secondPageResult = mockMvc.perform(
                        get("/api/v1/conversations")
                                .param("limit", "2")
                                .param("cursor", cursor)
                                .header(
                                        "Authorization",
                                        "Bearer " + tokens.accessToken()
                                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andReturn();
    
        var secondPage = objectMapper.readTree(
                secondPageResult.getResponse().getContentAsString()
        );
    
        var ids = new java.util.HashSet<String>();
    
        firstPage.get("items").forEach(
                item -> ids.add(item.get("id").stringValue())
        );
        secondPage.get("items").forEach(
                item -> ids.add(item.get("id").stringValue())
        );
    
        assertEquals(3, ids.size());
        assertTrue(ids.containsAll(java.util.List.of(
                first.toString(),
                second.toString(),
                third.toString()
        )));
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
                "Owner only"
        );
    
        mockMvc.perform(delete(
                                "/api/v1/conversations/{id}",
                                conversationId
                        )
                        .header(
                                "Authorization",
                                "Bearer " + other.accessToken()
                        ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error")
                        .value("resource_not_found"));
    
        mockMvc.perform(get("/api/v1/conversations")
                        .header(
                                "Authorization",
                                "Bearer " + owner.accessToken()
                        ))
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
                                "Bearer " + tokens.accessToken()
                        ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));
    
        mockMvc.perform(get("/api/v1/conversations")
                        .param("cursor", "not-a-cursor")
                        .header(
                                "Authorization",
                                "Bearer " + tokens.accessToken()
                        ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));
    
        mockMvc.perform(delete("/api/v1/conversations/not-a-uuid")
                        .header(
                                "Authorization",
                                "Bearer " + tokens.accessToken()
                        ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));
    }

    @Test
    void messagingRowsPersistThroughTheResponseLifecycle() {
        UserAccount user = userAccountRepository.saveAndFlush(
                new UserAccount(
                        "message-" + UUID.randomUUID() + "@example.com",
                        "test-password-hash"
                )
        );
    
        Conversation conversation = conversationRepository.saveAndFlush(
                new Conversation(user, "Messaging")
        );
    
        Message userMessage = messageRepository.saveAndFlush(
                new Message(
                        conversation,
                        MessageRole.USER,
                        "Hello"
                )
        );
    
        AiResponse response = aiResponseRepository.saveAndFlush(
                new AiResponse(
                        user,
                        conversation,
                        userMessage,
                        "MESSAGE_SEND",
                        "idem-" + UUID.randomUUID(),
                        "a".repeat(64)
                )
        );
    
        assertEquals(AiResponseStatus.PENDING, response.getStatus());
    
        AiProcessingAttempt attempt =
                aiProcessingAttemptRepository.saveAndFlush(
                        new AiProcessingAttempt(response, 1)
                );
    
        assertEquals(AttemptOutcome.IN_PROGRESS, attempt.getOutcome());
        assertNull(attempt.getFinishedAt());
    
        Message assistantMessage = messageRepository.saveAndFlush(
                new Message(
                        conversation,
                        MessageRole.ASSISTANT,
                        "Hi there"
                )
        );
    
        response.markCompleted(assistantMessage);
        attempt.succeed();
    
        aiResponseRepository.saveAndFlush(response);
        aiProcessingAttemptRepository.saveAndFlush(attempt);
    
        assertEquals(AiResponseStatus.COMPLETED, response.getStatus());
        assertEquals(
                assistantMessage.getId(),
                response.getAssistantMessage().getId()
        );
        assertEquals(AttemptOutcome.SUCCESS, attempt.getOutcome());
        assertNotNull(attempt.getFinishedAt());
    }
    
    @Test
    void aiResponseIdempotencyKeyIsUniquePerUserAndOperation() {
        UserAccount user = userAccountRepository.saveAndFlush(
                new UserAccount(
                        "idempotency-" + UUID.randomUUID() + "@example.com",
                        "test-password-hash"
                )
        );
    
        Conversation conversation = conversationRepository.saveAndFlush(
                new Conversation(user, "Idempotency")
        );
    
        Message firstMessage = messageRepository.saveAndFlush(
                new Message(conversation, MessageRole.USER, "First")
        );
    
        Message secondMessage = messageRepository.saveAndFlush(
                new Message(conversation, MessageRole.USER, "Second")
        );
    
        String key = "idem-" + UUID.randomUUID();
    
        aiResponseRepository.saveAndFlush(
                new AiResponse(
                        user,
                        conversation,
                        firstMessage,
                        "MESSAGE_SEND",
                        key,
                        "a".repeat(64)
                )
        );
    
        assertThrows(
                DataIntegrityViolationException.class,
                () -> aiResponseRepository.saveAndFlush(
                        new AiResponse(
                                user,
                                conversation,
                                secondMessage,
                                "MESSAGE_SEND",
                                key,
                                "b".repeat(64)
                        )
                )
        );
    }

    @Test
    void sendMessagePersistsAndCompletesResponse() throws Exception {
        String email = "send-" + UUID.randomUUID() + "@example.com";
        String password = "Correct-Horse-123!";
    
        register(email, password);
        LoginTokens tokens = login(email, password);
        UUID conversationId = createConversation(
                tokens.accessToken(),
                "Send test"
        );
    
        mockAIProvider.setContent("Hello from MIRA");
    
        MvcResult result = sendMessage(
                tokens.accessToken(),
                conversationId,
                "Hello",
                "send-" + UUID.randomUUID()
        );
    
        assertEquals(201, result.getResponse().getStatus());
    
        var body = objectMapper.readTree(
                result.getResponse().getContentAsString()
        );
    
        assertEquals(
                "COMPLETED",
                body.get("assistantResponse")
                        .get("status")
                        .stringValue()
        );
        assertEquals(
                "Hello from MIRA",
                body.get("assistantResponse")
                        .get("content")
                        .stringValue()
        );
    
        UUID responseId = UUID.fromString(
                body.get("assistantResponse")
                        .get("id")
                        .stringValue()
        );
    
        mockMvc.perform(get(
                                "/api/v1/conversations/{id}/messages",
                                conversationId
                        )
                        .header(
                                "Authorization",
                                "Bearer " + tokens.accessToken()
                        ))
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
                "Idempotent send"
        );
    
        String key = "send-" + UUID.randomUUID();
    
        MvcResult first = sendMessage(
                tokens.accessToken(),
                conversationId,
                "Hello",
                key
        );
    
        MvcResult second = sendMessage(
                tokens.accessToken(),
                conversationId,
                "Hello",
                key
        );
    
        assertEquals(201, first.getResponse().getStatus());
        assertEquals(201, second.getResponse().getStatus());
    
        var firstBody = objectMapper.readTree(
                first.getResponse().getContentAsString()
        );
        var secondBody = objectMapper.readTree(
                second.getResponse().getContentAsString()
        );
    
        assertEquals(
                firstBody.get("userMessage").get("id").stringValue(),
                secondBody.get("userMessage").get("id").stringValue()
        );
        assertEquals(
                firstBody.get("assistantResponse").get("id").stringValue(),
                secondBody.get("assistantResponse").get("id").stringValue()
        );
    
        mockMvc.perform(get(
                                "/api/v1/conversations/{id}/messages",
                                conversationId
                        )
                        .header(
                                "Authorization",
                                "Bearer " + tokens.accessToken()
                        ))
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
                "Conflict test"
        );
    
        String key = "send-" + UUID.randomUUID();
    
        sendMessage(
                tokens.accessToken(),
                conversationId,
                "First content",
                key
        );
    
        mockMvc.perform(post(
                                "/api/v1/conversations/{id}/messages",
                                conversationId
                        )
                        .header(
                                "Authorization",
                                "Bearer " + tokens.accessToken()
                        )
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.mira.mira_backend.dto.SendMessageRequest(
                                        "Different content"
                                )
                        )))
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
                "Timeout test"
        );
    
        mockAIProvider.setScenario(MockAIProvider.Scenario.TIMEOUT);
    
        MvcResult result = sendMessage(
                tokens.accessToken(),
                conversationId,
                "Hello",
                "timeout-" + UUID.randomUUID()
        );
    
        assertEquals(200, result.getResponse().getStatus());
    
        var body = objectMapper.readTree(
                result.getResponse().getContentAsString()
        );
    
        assertEquals(
                "UNKNOWN",
                body.get("assistantResponse")
                        .get("status")
                        .stringValue()
        );
    
        mockMvc.perform(get(
                                "/api/v1/conversations/{id}/messages",
                                conversationId
                        )
                        .header(
                                "Authorization",
                                "Bearer " + tokens.accessToken()
                        ))
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
                "Owner only"
        );
    
        MvcResult result = sendMessage(
                other.accessToken(),
                conversationId,
                "Intrusion attempt",
                "send-" + UUID.randomUUID()
        );
    
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
                "No key"
        );
    
        mockMvc.perform(post(
                                "/api/v1/conversations/{id}/messages",
                                conversationId
                        )
                        .header(
                                "Authorization",
                                "Bearer " + tokens.accessToken()
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.mira.mira_backend.dto.SendMessageRequest(
                                        "Hello"
                                )
                        )))
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
                "Retry test"
        );
    
        mockAIProvider.setScenario(MockAIProvider.Scenario.TIMEOUT);
    
        MvcResult sendResult = sendMessage(
                tokens.accessToken(),
                conversationId,
                "Hello",
                "send-" + UUID.randomUUID()
        );
    
        assertEquals(200, sendResult.getResponse().getStatus());
    
        UUID responseId = responseIdFrom(sendResult);
    
        mockAIProvider.setScenario(MockAIProvider.Scenario.SUCCESS);
        mockAIProvider.setContent("Recovered response");
    
        MvcResult retryResult = retryResponse(
                tokens.accessToken(),
                conversationId,
                responseId,
                "retry-" + UUID.randomUUID()
        );
    
        assertEquals(200, retryResult.getResponse().getStatus());
    
        var body = objectMapper.readTree(
                retryResult.getResponse().getContentAsString()
        );
    
        assertEquals(
                responseId.toString(),
                body.get("assistantResponse")
                        .get("id")
                        .stringValue()
        );
        assertEquals(
                "COMPLETED",
                body.get("assistantResponse")
                        .get("status")
                        .stringValue()
        );
        assertEquals(
                "Recovered response",
                body.get("assistantResponse")
                        .get("content")
                        .stringValue()
        );
    
        mockMvc.perform(get(
                                "/api/v1/conversations/{id}/messages",
                                conversationId
                        )
                        .header(
                                "Authorization",
                                "Bearer " + tokens.accessToken()
                        ))
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
                "Retry idempotency"
        );
    
        mockAIProvider.setScenario(MockAIProvider.Scenario.TIMEOUT);
    
        MvcResult sendResult = sendMessage(
                tokens.accessToken(),
                conversationId,
                "Hello",
                "send-" + UUID.randomUUID()
        );
    
        UUID responseId = responseIdFrom(sendResult);
    
        mockAIProvider.setScenario(MockAIProvider.Scenario.SUCCESS);
        mockAIProvider.setContent("Only once");
    
        String retryKey = "retry-" + UUID.randomUUID();
    
        MvcResult first = retryResponse(
                tokens.accessToken(),
                conversationId,
                responseId,
                retryKey
        );
    
        MvcResult second = retryResponse(
                tokens.accessToken(),
                conversationId,
                responseId,
                retryKey
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
                firstBody.get("assistantResponse").get("id").stringValue(),
                secondBody.get("assistantResponse").get("id").stringValue()
        );
    
        mockMvc.perform(get(
                                "/api/v1/conversations/{id}/messages",
                                conversationId
                        )
                        .header(
                                "Authorization",
                                "Bearer " + tokens.accessToken()
                        ))
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
                "Retry limit"
        );
    
        mockAIProvider.setScenario(MockAIProvider.Scenario.TIMEOUT);
    
        MvcResult sendResult = sendMessage(
                tokens.accessToken(),
                conversationId,
                "Hello",
                "send-" + UUID.randomUUID()
        );
    
        UUID responseId = responseIdFrom(sendResult);
    
        // Attempt 2
        assertEquals(
                200,
                retryResponse(
                        tokens.accessToken(),
                        conversationId,
                        responseId,
                        "retry-2-" + UUID.randomUUID()
                ).getResponse().getStatus()
        );
    
        // Attempt 3
        assertEquals(
                200,
                retryResponse(
                        tokens.accessToken(),
                        conversationId,
                        responseId,
                        "retry-3-" + UUID.randomUUID()
                ).getResponse().getStatus()
        );
    
        // Attempt 4 must be rejected
        mockMvc.perform(post(
                                "/api/v1/conversations/{cid}/responses/{rid}/retry",
                                conversationId,
                                responseId
                        )
                        .header(
                                "Authorization",
                                "Bearer " + tokens.accessToken()
                        )
                        .header(
                                "Idempotency-Key",
                                "retry-4-" + UUID.randomUUID()
                        ))
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
                "Completed retry"
        );
    
        mockAIProvider.setScenario(MockAIProvider.Scenario.SUCCESS);
        mockAIProvider.setContent("Done");
    
        MvcResult sendResult = sendMessage(
                tokens.accessToken(),
                conversationId,
                "Hello",
                "send-" + UUID.randomUUID()
        );
    
        UUID responseId = responseIdFrom(sendResult);
    
        mockMvc.perform(post(
                                "/api/v1/conversations/{cid}/responses/{rid}/retry",
                                conversationId,
                                responseId
                        )
                        .header(
                                "Authorization",
                                "Bearer " + tokens.accessToken()
                        )
                        .header(
                                "Idempotency-Key",
                                "retry-" + UUID.randomUUID()
                        ))
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
                "Retry owner"
        );
    
        mockAIProvider.setScenario(MockAIProvider.Scenario.TIMEOUT);
    
        MvcResult sendResult = sendMessage(
                owner.accessToken(),
                conversationId,
                "Hello",
                "send-" + UUID.randomUUID()
        );
    
        UUID responseId = responseIdFrom(sendResult);
    
        mockMvc.perform(post(
                                "/api/v1/conversations/{cid}/responses/{rid}/retry",
                                conversationId,
                                responseId
                        )
                        .header(
                                "Authorization",
                                "Bearer " + other.accessToken()
                        )
                        .header(
                                "Idempotency-Key",
                                "retry-" + UUID.randomUUID()
                        ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error")
                        .value("resource_not_found"));
    }

    private static String newTokenHash() {
        return UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
    }

    private void register(String email, String password) throws Exception {
    mockMvc.perform(post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                            new com.mira.mira_backend.dto.RegisterRequest(
                                    email,
                                    password
                            )
                    )))
            .andExpect(status().isCreated());
    }
    
    private LoginTokens login(String email, String password)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.mira.mira_backend.dto.LoginRequest(
                                        email,
                                        password
                                )
                        )))
                .andExpect(status().isOk())
                .andReturn();
    
        var body = objectMapper.readTree(
                result.getResponse().getContentAsString()
        );
    
        return new LoginTokens(
                body.get("accessToken").stringValue(),
                body.get("refreshToken").stringValue(),
                UUID.fromString(body.get("sessionId").stringValue())
        );
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
                "Rate limit test"
        );
    
        for (int index = 0; index < 10; index++) {
            MvcResult result = sendMessage(
                    tokens.accessToken(),
                    conversationId,
                    "Message " + index,
                    "rate-" + index + "-" + UUID.randomUUID()
            );
    
            assertEquals(201, result.getResponse().getStatus());
        }
    
        mockMvc.perform(post(
                                "/api/v1/conversations/{id}/messages",
                                conversationId
                        )
                        .header(
                                "Authorization",
                                "Bearer " + tokens.accessToken()
                        )
                        .header(
                                "Idempotency-Key",
                                "rate-final-" + UUID.randomUUID()
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.mira.mira_backend.dto.SendMessageRequest(
                                        "One too many"
                                )
                        )))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("rate_limited"));
    }

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
                                "Bearer " + tokens.accessToken()
                        ))
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
                                "Bearer " + tokens.accessToken()
                        )
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
                                "Bearer " + tokens.accessToken()
                        )
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
                                "Bearer " + tokens.accessToken()
                        )
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
                                "Bearer " + tokens.accessToken()
                        )
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
                                "Bearer " + tokens.accessToken()
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.version").value(2));
    
        mockMvc.perform(patch("/api/v1/memory/style/settings")
                        .header(
                                "Authorization",
                                "Bearer " + tokens.accessToken()
                        )
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
                                "Bearer " + tokens.accessToken()
                        )
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
                                "Bearer " + tokens.accessToken()
                        ))
                .andExpect(status().isNoContent());
    
        int after = memoryControlRepository.findById(userId)
                .orElseThrow()
                .getGeneration();
    
        assertEquals(before + 1, after);
    
        mockMvc.perform(get("/api/v1/memory/style")
                        .header(
                                "Authorization",
                                "Bearer " + tokens.accessToken()
                        ))
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
                "Memory injection"
        );
    
        mockMvc.perform(put("/api/v1/memory/style")
                        .header(
                                "Authorization",
                                "Bearer " + tokens.accessToken()
                        )
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
                "memory-send-" + UUID.randomUUID()
        );
    
        assertEquals(201, sendResult.getResponse().getStatus());
    
        AIRequest request = mockAIProvider.lastRequest();
    
        assertNotNull(request);
        assertTrue(requestHasSystemContent(
                request,
                "communication-style preferences"
        ));
        assertTrue(requestHasSystemContent(
                request,
                "explanation_depth: detailed"
        ));
        assertTrue(requestHasSystemContent(
                request,
                "tone: formal"
        ));
    
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
                "Memory disabled"
        );
    
        mockMvc.perform(put("/api/v1/memory/style")
                        .header(
                                "Authorization",
                                "Bearer " + tokens.accessToken()
                        )
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
                                "Bearer " + tokens.accessToken()
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk());
    
        mockAIProvider.setContent("Plain reply");
    
        sendMessage(
                tokens.accessToken(),
                conversationId,
                "Hello",
                "memory-disabled-send-" + UUID.randomUUID()
        );
    
        AIRequest request = mockAIProvider.lastRequest();
    
        assertNotNull(request);
        assertFalse(requestHasSystemContent(
                request,
                "communication-style preferences"
        ));
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
                "Memory empty"
        );
    
        mockAIProvider.setContent("Plain reply");
    
        sendMessage(
                tokens.accessToken(),
                conversationId,
                "Hello",
                "memory-empty-send-" + UUID.randomUUID()
        );
    
        AIRequest request = mockAIProvider.lastRequest();
    
        assertNotNull(request);
        assertFalse(requestHasSystemContent(
                request,
                "communication-style preferences"
        ));
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
                "Memory weak"
        );
    
        mockMvc.perform(put("/api/v1/memory/style")
                        .header(
                                "Authorization",
                                "Bearer " + tokens.accessToken()
                        )
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
                "memory-weak-send-" + UUID.randomUUID()
        );
    
        AIRequest request = mockAIProvider.lastRequest();
    
        assertNotNull(request);
        assertFalse(requestHasSystemContent(
                request,
                "communication-style preferences"
        ));
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
                "Extraction job"
        );
    
        mockAIProvider.setContent("Completed reply");
    
        MvcResult result = sendMessage(
                tokens.accessToken(),
                conversationId,
                "Hello",
                "extraction-" + UUID.randomUUID()
        );
    
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
                "No extraction on timeout"
        );
    
        mockAIProvider.setScenario(MockAIProvider.Scenario.TIMEOUT);
    
        MvcResult result = sendMessage(
                tokens.accessToken(),
                conversationId,
                "Hello",
                "extraction-timeout-" + UUID.randomUUID()
        );
    
        assertEquals(200, result.getResponse().getStatus());
    
        UUID responseId = responseIdFrom(result);
    
        assertTrue(
                memoryExtractionJobRepository
                        .findByResponseId(responseId)
                        .isEmpty()
        );
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
                "Disabled extraction"
        );
    
        mockMvc.perform(patch("/api/v1/memory/style/settings")
                        .header(
                                "Authorization",
                                "Bearer " + tokens.accessToken()
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk());
    
        mockAIProvider.setContent("Completed reply");
    
        MvcResult result = sendMessage(
                tokens.accessToken(),
                conversationId,
                "Hello",
                "extraction-disabled-" + UUID.randomUUID()
        );
    
        UUID responseId = responseIdFrom(result);
    
        assertTrue(
                memoryExtractionJobRepository
                        .findByResponseId(responseId)
                        .isEmpty()
        );
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
                "Explicit extraction"
        );
    
        mockAIProvider.setContent("Assistant reply");
    
        MvcResult sendResult = sendMessage(
                tokens.accessToken(),
                conversationId,
                "Always use a formal tone.",
                "extract-explicit-" + UUID.randomUUID()
        );
    
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
                                "Bearer " + tokens.accessToken()
                        ))
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
                "Inferred extraction"
        );
    
        mockAIProvider.setContent("Assistant reply");
    
        MvcResult sendResult = sendMessage(
                tokens.accessToken(),
                conversationId,
                "I usually prefer academic explanations.",
                "extract-inferred-" + UUID.randomUUID()
        );
    
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
    
        UUID userId = userAccountRepository
                .findByNormalizedEmail(email)
                .orElseThrow()
                .getId();
    
        UserMemoryCandidate candidate =
                userMemoryCandidateRepository
                        .findByUserIdAndFieldAndValueAndStatus(
                                userId,
                                "tone",
                                "academic",
                                MemoryCandidateStatus.PENDING
                        )
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
                "Stale extraction"
        );
    
        mockAIProvider.setContent("Assistant reply");
    
        MvcResult sendResult = sendMessage(
                tokens.accessToken(),
                conversationId,
                "Always use a formal tone.",
                "extract-stale-" + UUID.randomUUID()
        );
    
        UUID responseId = responseIdFrom(sendResult);
    
        MemoryExtractionJob job = memoryExtractionJobRepository
                .findByResponseId(responseId)
                .orElseThrow();
    
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
    
        memoryExtractionWorker.processJob(job.getId());
    
        assertEquals(
                MemoryJobStatus.SKIPPED,
                memoryExtractionJobRepository
                        .findById(job.getId())
                        .orElseThrow()
                        .getStatus()
        );
    
        mockMvc.perform(get("/api/v1/memory/style")
                        .header(
                                "Authorization",
                                "Bearer " + tokens.accessToken()
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile").isEmpty());
    }
    
    @Test
    void malformedExtractionOutputFailsTheJob() throws Exception {
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
    
        MemoryExtractionJob failed = memoryExtractionJobRepository
                .findById(job.getId())
                .orElseThrow();
    
        assertEquals(MemoryJobStatus.FAILED, failed.getStatus());
        assertEquals("INVALID_OUTPUT", failed.getErrorCategory());
    }
    
    private record LoginTokens(
            String accessToken,
            String refreshToken,
            UUID sessionId
    ) {
    }

    private UUID createConversation(
            String accessToken,
            String title
    ) throws Exception {
        MvcResult result = mockMvc.perform(
                        post("/api/v1/conversations")
                                .header(
                                        "Authorization",
                                        "Bearer " + accessToken
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        new com.mira.mira_backend.dto.CreateConversationRequest(
                                                title
                                        )
                                )))
                .andExpect(status().isCreated())
                .andReturn();
    
        return UUID.fromString(
                objectMapper.readTree(
                                result.getResponse().getContentAsString()
                        )
                        .get("id")
                        .stringValue()
        );
    }

    private MvcResult sendMessage(
        String accessToken,
        UUID conversationId,
        String content,
        String idempotencyKey
    ) throws Exception {
        return mockMvc.perform(
                        post(
                                "/api/v1/conversations/{id}/messages",
                                conversationId
                        )
                                .header(
                                        "Authorization",
                                        "Bearer " + accessToken
                                )
                                .header("Idempotency-Key", idempotencyKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        new com.mira.mira_backend.dto.SendMessageRequest(
                                                content
                                        )
                                )))
                .andReturn();
    }

    private UUID responseIdFrom(MvcResult result) throws Exception {
    return UUID.fromString(
            objectMapper.readTree(
                            result.getResponse().getContentAsString()
                    )
                    .get("assistantResponse")
                    .get("id")
                    .stringValue()
        );
    }

    private MvcResult retryResponse(
            String accessToken,
            UUID conversationId,
            UUID responseId,
            String idempotencyKey
    ) throws Exception {
        return mockMvc.perform(
                        post(
                                "/api/v1/conversations/{cid}/responses/{rid}/retry",
                                conversationId,
                                responseId
                        )
                                .header(
                                        "Authorization",
                                        "Bearer " + accessToken
                                )
                                .header(
                                        "Idempotency-Key",
                                        idempotencyKey
                                ))
                .andReturn();
    }

    private boolean requestHasSystemContent(
        AIRequest request,
        String text
    ) {
        for (AIMessage message : request.messages()) {
            if (message.role() == AIRole.SYSTEM
                    && message.content().contains(text)) {
                return true;
            }
        }

        return false;
    }
}