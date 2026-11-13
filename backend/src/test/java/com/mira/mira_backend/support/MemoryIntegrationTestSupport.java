package com.mira.mira_backend.support;

import com.mira.mira_backend.ai.AIMessage;
import com.mira.mira_backend.ai.AIRequest;
import com.mira.mira_backend.ai.AIRole;
import com.mira.mira_backend.ai.MockAIProvider;
import com.mira.mira_backend.entity.MemoryExtractionJob;
import com.mira.mira_backend.repository.MemoryExtractionJobRepository;
import com.mira.mira_backend.repository.UserAccountRepository;
import com.mira.mira_backend.service.MemoryExtractionWorker;

import org.junit.jupiter.api.BeforeEach;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public abstract class MemoryIntegrationTestSupport {

    @SuppressWarnings("resource")
    protected static final PostgreSQLContainer POSTGRES;

    static {
        POSTGRES = new PostgreSQLContainer(
                DockerImageName.parse("postgres:18-alpine"))
                .withDatabaseName("mira_test")
                .withUsername("mira_test")
                .withPassword("mira_test");
        POSTGRES.start();
    }

    @DynamicPropertySource
    protected static void configureDatasource(
            DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name",
                POSTGRES::getDriverClassName);
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected UserAccountRepository userAccountRepository;

    @Autowired
    protected MockAIProvider mockAIProvider;

    @Autowired
    protected MemoryExtractionWorker memoryExtractionWorker;

    @Autowired
    protected MemoryExtractionJobRepository memoryExtractionJobRepository;

    @BeforeEach
    protected void resetMockProvider() {
        mockAIProvider.reset();
    }

    protected record LoginTokens(
            String email,
            String accessToken,
            String refreshToken,
            UUID sessionId) {
    }

    protected LoginTokens newUser(String prefix) throws Exception {
        String email = prefix + "-" + UUID.randomUUID() + "@example.com";
        String password = "Correct-Horse-123!";

        register(email, password);

        return login(email, password);
    }

    protected void register(String email, String password) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new com.mira.mira_backend.dto.RegisterRequest(
                                email,
                                password))))
                .andExpect(status().isCreated());
    }

    protected LoginTokens login(String email, String password)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new com.mira.mira_backend.dto.LoginRequest(
                                email,
                                password))))
                .andExpect(status().isOk())
                .andReturn();

        var body = objectMapper.readTree(
                result.getResponse().getContentAsString());

        return new LoginTokens(
                email,
                body.get("accessToken").stringValue(),
                body.get("refreshToken").stringValue(),
                UUID.fromString(body.get("sessionId").stringValue()));
    }

    protected UUID createConversation(
            String accessToken,
            String title) throws Exception {
        MvcResult result = mockMvc.perform(
                post("/api/v1/conversations")
                        .header(
                                "Authorization",
                                "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.mira.mira_backend.dto.CreateConversationRequest(
                                        title))))
                .andExpect(status().isCreated())
                .andReturn();

        return UUID.fromString(
                objectMapper.readTree(
                        result.getResponse().getContentAsString())
                        .get("id")
                        .stringValue());
    }

    protected MvcResult sendMessage(
            String accessToken,
            UUID conversationId,
            String content,
            String idempotencyKey) throws Exception {
        return mockMvc.perform(
                post(
                        "/api/v1/conversations/{id}/messages",
                        conversationId)
                        .header(
                                "Authorization",
                                "Bearer " + accessToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.mira.mira_backend.dto.SendMessageRequest(
                                        content))))
                .andReturn();
    }

    protected UUID responseIdFrom(MvcResult result) throws Exception {
        return UUID.fromString(
                objectMapper.readTree(
                        result.getResponse().getContentAsString())
                        .get("assistantResponse")
                        .get("id")
                        .stringValue());
    }

    protected MemoryExtractionJob sendAndGetJob(
            LoginTokens user,
            UUID conversationId,
            String content,
            String idempotencyKey) throws Exception {
        MvcResult result = sendMessage(
                user.accessToken(),
                conversationId,
                content,
                idempotencyKey);

        return memoryExtractionJobRepository
                .findByResponseId(responseIdFrom(result))
                .orElseThrow();
    }

    protected void runExtraction(
            MemoryExtractionJob job,
            String providerJson) {
        mockAIProvider.setContent(providerJson);
        memoryExtractionWorker.processJob(job.getId());
    }

    protected boolean requestHasSystemContent(
            AIRequest request,
            String text) {
        for (AIMessage message : request.messages()) {
            if (message.role() == AIRole.SYSTEM
                    && message.content().contains(text)) {
                return true;
            }
        }

        return false;
    }

    protected UUID userIdFor(String email) {
        return userAccountRepository
                .findByNormalizedEmail(email)
                .orElseThrow()
                .getId();
    }
}
