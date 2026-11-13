package com.mira.mira_backend.memory;

import com.mira.mira_backend.entity.MemoryCandidateClassification;
import com.mira.mira_backend.entity.MemoryCandidateStatus;
import com.mira.mira_backend.entity.UserMemoryCandidate;
import com.mira.mira_backend.repository.UserMemoryCandidateRepository;
import com.mira.mira_backend.support.MemoryIntegrationTestSupport;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MemoryEndpointSecurityTest extends MemoryIntegrationTestSupport {

    @Autowired
    private UserMemoryCandidateRepository candidateRepository;

    @Test
    void everyMemoryEndpointRequiresAuthentication() throws Exception {
        UUID candidateId = UUID.randomUUID();

        assertUnauthorized(get("/api/v1/memory/style"));

        assertUnauthorized(put("/api/v1/memory/style")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":1,\"profile\":{}}"));

        assertUnauthorized(patch("/api/v1/memory/style/settings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false}"));

        assertUnauthorized(delete("/api/v1/memory/style"));

        assertUnauthorized(get("/api/v1/memory/candidates"));

        assertUnauthorized(post(
                "/api/v1/memory/candidates/{id}/accept",
                candidateId));

        assertUnauthorized(post(
                "/api/v1/memory/candidates/{id}/reject",
                candidateId));
    }

    @Test
    void memoryMutationsAndCandidatesAreScopedToTheOwner()
            throws Exception {
        LoginTokens alice = newUser("isolation-alice");
        LoginTokens bob = newUser("isolation-bob");

        putProfile(alice, 1, "{\"tone\":\"formal\"}");
        putProfile(bob, 1, "{\"tone\":\"academic\"}");

        mockMvc.perform(patch("/api/v1/memory/style/settings")
                .header(
                        "Authorization",
                        "Bearer " + bob.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false}"))
                .andExpect(status().isOk());

        // Bob's writes did not touch Alice's profile.
        mockMvc.perform(get("/api/v1/memory/style")
                .header(
                        "Authorization",
                        "Bearer " + alice.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.profile.tone").value("formal"));

        UserMemoryCandidate candidate = candidateRepository.save(
                new UserMemoryCandidate(
                        userIdFor(alice.email()),
                        "tone",
                        "conversational",
                        MemoryCandidateClassification.INFERRED,
                        "Alice evidence",
                        1));

        // Bob cannot list Alice's candidate.
        mockMvc.perform(get("/api/v1/memory/candidates")
                .header(
                        "Authorization",
                        "Bearer " + bob.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());

        // Bob cannot accept or reject Alice's candidate.
        mockMvc.perform(post(
                "/api/v1/memory/candidates/{id}/accept",
                candidate.getId())
                .header(
                        "Authorization",
                        "Bearer " + bob.accessToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error")
                        .value("resource_not_found"));

        mockMvc.perform(post(
                "/api/v1/memory/candidates/{id}/reject",
                candidate.getId())
                .header(
                        "Authorization",
                        "Bearer " + bob.accessToken()))
                .andExpect(status().isNotFound());

        // Bob's purge does not affect Alice's profile or candidate.
        mockMvc.perform(delete("/api/v1/memory/style")
                .header(
                        "Authorization",
                        "Bearer " + bob.accessToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/memory/style")
                .header(
                        "Authorization",
                        "Bearer " + alice.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.tone").value("formal"));

        assertEquals(
                MemoryCandidateStatus.PENDING,
                candidateRepository
                        .findById(candidate.getId())
                        .orElseThrow()
                        .getStatus());
    }

    private void putProfile(
            LoginTokens user,
            int expectedVersion,
            String profileJson) throws Exception {
        mockMvc.perform(put("/api/v1/memory/style")
                .header(
                        "Authorization",
                        "Bearer " + user.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":"
                        + expectedVersion
                        + ",\"profile\":" + profileJson + "}"))
                .andExpect(status().isOk());
    }

    private void assertUnauthorized(
            MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error")
                        .value("authentication_required"));
    }
}