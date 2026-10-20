package com.mira.mira_backend;

import com.mira.mira_backend.entity.UserAccount;
import com.mira.mira_backend.entity.UserSession;
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

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.UUID;

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
import org.springframework.test.web.servlet.MvcResult;

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
}