package com.mira.mira_backend.memory;

import com.mira.mira_backend.entity.MemoryCandidateStatus;
import com.mira.mira_backend.entity.MemoryExtractionJob;
import com.mira.mira_backend.entity.MemoryJobStatus;
import com.mira.mira_backend.entity.UserMemoryCandidate;
import com.mira.mira_backend.repository.UserMemoryCandidateRepository;
import com.mira.mira_backend.support.MemoryIntegrationTestSupport;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MemoryConflictResolutionTest extends MemoryIntegrationTestSupport {

    @Autowired
    private UserMemoryCandidateRepository candidateRepository;

    @Test
    void hybridPolicyAppliesCorrectionsOverDurablesAndDiscardsEphemeral()
            throws Exception {
        LoginTokens user = newUser("conflict-matrix");
        UUID conversationId = createConversation(
                user.accessToken(),
                "Hybrid matrix");

        putProfile(user, 1,
                "{\"tone\":\"formal\",\"formatting\":\"prose\"}");

        MemoryExtractionJob job = sendAndGetJob(
                user,
                conversationId,
                "From now on, be conversational and use bullet "
                        + "lists. Just this once, be concise.",
                "conflict-matrix-" + UUID.randomUUID());

        runExtraction(job, """
                {
                  "candidates": [
                    {
                      "field": "tone",
                      "value": "academic",
                      "classification": "EXPLICIT_DURABLE",
                      "evidence": "Use an academic tone."
                    },
                    {
                      "field": "tone",
                      "value": "conversational",
                      "classification": "EXPLICIT_CORRECTION",
                      "evidence": "Be conversational."
                    },
                    {
                      "field": "formatting",
                      "value": "bulleted_lists",
                      "classification": "EXPLICIT_CORRECTION",
                      "evidence": "Use bullet lists."
                    },
                    {
                      "field": "examples",
                      "value": "prefer_concrete_examples",
                      "classification": "INFERRED",
                      "evidence": "I like examples."
                    },
                    {
                      "field": "explanation_depth",
                      "value": "concise",
                      "classification": "EPHEMERAL",
                      "evidence": "Just this once, be concise."
                    }
                  ]
                }
                """);

        assertEquals(MemoryJobStatus.COMPLETED, jobStatus(job));

        mockMvc.perform(get("/api/v1/memory/style")
                .header(
                        "Authorization",
                        "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.profile.tone")
                        .value("conversational"))
                .andExpect(jsonPath("$.profile.formatting")
                        .value("bulleted_lists"))
                .andExpect(jsonPath("$.profile.examples")
                        .doesNotExist())
                .andExpect(jsonPath("$.profile.explanation_depth")
                        .doesNotExist());

        List<UserMemoryCandidate> pending = pendingCandidates(user);

        assertEquals(1, pending.size());
        assertEquals("examples", pending.get(0).getField());
    }

    @Test
    void repeatedInferredEvidenceIncrementsCountWithoutDuplicating()
            throws Exception {
        LoginTokens user = newUser("conflict-repeat");
        UUID conversationId = createConversation(
                user.accessToken(),
                "Repeated evidence");

        mockAIProvider.setContent("Assistant reply");

        MemoryExtractionJob first = sendAndGetJob(
                user,
                conversationId,
                "I like academic explanations.",
                "repeat-1-" + UUID.randomUUID());

        runExtraction(first,
                inferred("tone", "academic", "First observation"));

        mockAIProvider.setContent("Assistant reply");

        MemoryExtractionJob second = sendAndGetJob(
                user,
                conversationId,
                "Academic style suits me.",
                "repeat-2-" + UUID.randomUUID());

        runExtraction(second,
                inferred("tone", "academic", "Second observation"));

        List<UserMemoryCandidate> pending = pendingCandidates(user);

        assertEquals(1, pending.size());
        assertEquals(2, pending.get(0).getEvidenceCount());
        assertEquals("Second observation", pending.get(0).getEvidence());
    }

    @Test
    void inferredCandidateMatchingTheCurrentProfileIsNotStaged()
            throws Exception {
        LoginTokens user = newUser("conflict-current");
        UUID conversationId = createConversation(
                user.accessToken(),
                "Current match");

        putProfile(user, 1, "{\"tone\":\"formal\"}");

        MemoryExtractionJob job = sendAndGetJob(
                user,
                conversationId,
                "Formal works for me.",
                "current-match-" + UUID.randomUUID());

        runExtraction(job,
                inferred("tone", "formal", "Formal works for me."));

        assertEquals(MemoryJobStatus.COMPLETED, jobStatus(job));
        assertTrue(pendingCandidates(user).isEmpty());

        mockMvc.perform(get("/api/v1/memory/style")
                .header(
                        "Authorization",
                        "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.profile.tone").value("formal"));
    }

    @Test
    void inferredCandidateIsNotStagedWhenTheSameFieldIsExplicitlyUpdated()
            throws Exception {
        LoginTokens user = newUser("conflict-explicit-wins");
        UUID conversationId = createConversation(
                user.accessToken(),
                "Explicit wins");

        MemoryExtractionJob job = sendAndGetJob(
                user,
                conversationId,
                "Always be formal, though academic sounds nice.",
                "explicit-wins-" + UUID.randomUUID());

        runExtraction(job, """
                {
                  "candidates": [
                    {
                      "field": "tone",
                      "value": "formal",
                      "classification": "EXPLICIT_DURABLE",
                      "evidence": "Always be formal."
                    },
                    {
                      "field": "tone",
                      "value": "academic",
                      "classification": "INFERRED",
                      "evidence": "Academic sounds nice."
                    }
                  ]
                }
                """);

        assertEquals(MemoryJobStatus.COMPLETED, jobStatus(job));
        assertTrue(pendingCandidates(user).isEmpty());

        mockMvc.perform(get("/api/v1/memory/style")
                .header(
                        "Authorization",
                        "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.profile.tone").value("formal"));
    }

    @Test
    void malformedCandidatesAreDroppedWithoutChangingMemory()
            throws Exception {
        LoginTokens user = newUser("conflict-malformed");
        UUID conversationId = createConversation(
                user.accessToken(),
                "Malformed");

        MemoryExtractionJob job = sendAndGetJob(
                user,
                conversationId,
                "A normal turn.",
                "malformed-" + UUID.randomUUID());

        runExtraction(job, """
                {
                  "candidates": [
                    {
                      "field": "favourite_colour",
                      "value": "blue",
                      "classification": "EXPLICIT_DURABLE",
                      "evidence": "Blue."
                    },
                    {
                      "field": "tone",
                      "value": "sarcastic",
                      "classification": "INFERRED",
                      "evidence": "Sarcastic."
                    },
                    {
                      "field": "formatting",
                      "classification": "INFERRED",
                      "evidence": "Missing value."
                    },
                    {
                      "field": "examples",
                      "value": "theory_first",
                      "classification": "NOT_A_CLASSIFICATION",
                      "evidence": "Bad class."
                    }
                  ]
                }
                """);

        assertEquals(MemoryJobStatus.COMPLETED, jobStatus(job));
        assertTrue(pendingCandidates(user).isEmpty());

        mockMvc.perform(get("/api/v1/memory/style")
                .header(
                        "Authorization",
                        "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.profile").isEmpty());
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

    private MemoryJobStatus jobStatus(MemoryExtractionJob job) {
        return memoryExtractionJobRepository
                .findById(job.getId())
                .orElseThrow()
                .getStatus();
    }

    private List<UserMemoryCandidate> pendingCandidates(
            LoginTokens user) {
        return candidateRepository
                .findByUserIdAndStatusOrderByCreatedAtDesc(
                        userIdFor(user.email()),
                        MemoryCandidateStatus.PENDING);
    }

    private String inferred(
            String field,
            String value,
            String evidence) {
        return "{\"candidates\":[{\"field\":\"" + field
                + "\",\"value\":\"" + value
                + "\",\"classification\":\"INFERRED\","
                + "\"evidence\":\"" + evidence + "\"}]}";
    }
}