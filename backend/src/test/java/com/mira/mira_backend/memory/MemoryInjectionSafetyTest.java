package com.mira.mira_backend.memory;

import com.mira.mira_backend.ai.AIMessage;
import com.mira.mira_backend.ai.AIRequest;
import com.mira.mira_backend.ai.AIRole;
import com.mira.mira_backend.support.MemoryIntegrationTestSupport;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MemoryInjectionSafetyTest extends MemoryIntegrationTestSupport {

    @Test
    void currentUserInstructionIsLastAndAdvisoryIsExplicitlySubordinate()
            throws Exception {
        LoginTokens user = newUser("inject-dominance");
        UUID conversationId = createConversation(
                user.accessToken(),
                "Dominance");

        mockMvc.perform(put("/api/v1/memory/style")
                .header(
                        "Authorization",
                        "Bearer " + user.accessToken())
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

        String instruction = "Answer in exactly one sentence.";

        MvcResult result = sendMessage(
                user.accessToken(),
                conversationId,
                instruction,
                "dominance-" + UUID.randomUUID());

        assertEquals(201, result.getResponse().getStatus());

        AIRequest request = mockAIProvider.lastRequest();

        assertNotNull(request);

        List<AIMessage> messages = request.messages();
        AIMessage last = messages.get(messages.size() - 1);

        assertEquals(AIRole.USER, last.role());
        assertEquals(instruction, last.content());

        AIMessage styleMessage = messages.stream()
                .filter(message -> message.role() == AIRole.SYSTEM)
                .filter(message -> message.content()
                        .contains("communication-style preferences"))
                .findFirst()
                .orElseThrow();

        assertTrue(styleMessage.content().contains(
                "Current explicit user instructions always take "
                        + "precedence"));
        assertTrue(styleMessage.content().contains(
                "explanation_depth: detailed"));
        assertTrue(styleMessage.content().contains("tone: formal"));

        int styleIndex = messages.indexOf(styleMessage);
        int instructionIndex = messages.size() - 1;

        assertTrue(styleIndex < instructionIndex);

        AIMessage baseInstruction = messages.get(0);

        assertEquals(AIRole.SYSTEM, baseInstruction.role());
        assertTrue(baseInstruction.content().contains(
                "Follow the user's current instructions over any "
                        + "prior preference."));
    }

    @Test
    void purgedMemoryIsNotInjectedIntoTheNextChatRequest()
            throws Exception {
        LoginTokens user = newUser("inject-purge");
        UUID conversationId = createConversation(
                user.accessToken(),
                "Purge injection");

        mockMvc.perform(put("/api/v1/memory/style")
                .header(
                        "Authorization",
                        "Bearer " + user.accessToken())
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

        mockMvc.perform(delete("/api/v1/memory/style")
                .header(
                        "Authorization",
                        "Bearer " + user.accessToken()))
                .andExpect(status().isNoContent());

        mockAIProvider.setContent("Plain reply");

        sendMessage(
                user.accessToken(),
                conversationId,
                "Hello",
                "purge-inject-" + UUID.randomUUID());

        AIRequest request = mockAIProvider.lastRequest();

        assertNotNull(request);
        assertFalse(requestHasSystemContent(
                request,
                "communication-style preferences"));
    }
}