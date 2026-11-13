package com.mira.mira_backend.auth;

import com.mira.mira_backend.dto.LoginRequest;
import com.mira.mira_backend.dto.RefreshRequest;
import com.mira.mira_backend.repository.UserSessionRepository;
import com.mira.mira_backend.support.MemoryIntegrationTestSupport;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthSecurityTest extends MemoryIntegrationTestSupport {

    @Autowired
    private UserSessionRepository userSessionRepository;

    @Test
    void replayedRefreshTokenRevokesEverySessionForTheAccount()
            throws Exception {
        LoginTokens first = newUser("replay-first");
        LoginTokens second = login(
                first.email(),
                "Correct-Horse-123!"
        );

        refresh(first.refreshToken());

        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new RefreshRequest(first.refreshToken()))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error")
                        .value("invalid_credentials"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new RefreshRequest(second.refreshToken()))))
                .andExpect(status().isUnauthorized());

        assertNotNull(userSessionRepository
                .findById(first.sessionId())
                .orElseThrow()
                .getRevokedAt());

        assertNotNull(userSessionRepository
                .findById(second.sessionId())
                .orElseThrow()
                .getRevokedAt());
    }

    @Test
    void replayRevocationDoesNotAffectAnotherAccount() throws Exception {
        LoginTokens alice = newUser("replay-alice");
        LoginTokens bob = newUser("replay-bob");

        refresh(alice.refreshToken());

        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new RefreshRequest(alice.refreshToken()))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new RefreshRequest(bob.refreshToken()))))
                .andExpect(status().isOk());
    }

    @Test
    void repeatedFailedLoginsForOneEmailAreRateLimited()
            throws Exception {
        String email = "auth-limit-" + UUID.randomUUID()
                + "@example.com";
        String password = "Wrong-Password-123!";

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                            new LoginRequest(email, password))))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new LoginRequest(email, password))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("rate_limited"));

        String other = "auth-limit-other-" + UUID.randomUUID()
                + "@example.com";

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new LoginRequest(other, password))))
                .andExpect(status().isUnauthorized());
    }

    private void refresh(String refreshToken) throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new RefreshRequest(refreshToken))))
                .andExpect(status().isOk());
    }
}
