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

import com.mira.mira_backend.repository.AiProcessingAttemptRepository;
import com.mira.mira_backend.repository.AiResponseRepository;
import com.mira.mira_backend.repository.MessageRepository;
import com.mira.mira_backend.repository.ConversationRepository;
import com.mira.mira_backend.repository.UserAccountRepository;
import com.mira.mira_backend.repository.UserSessionRepository;

import com.mira.mira_backend.service.DuplicateEmailException;
import com.mira.mira_backend.service.InvalidRegistrationException;
import com.mira.mira_backend.service.UserAccountService;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.data.domain.PageRequest;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.UUID;
import java.util.List;

import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class MiraBackendApplicationTests {

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
}