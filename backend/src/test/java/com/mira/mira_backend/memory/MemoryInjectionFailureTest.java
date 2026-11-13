package com.mira.mira_backend.memory;

import com.mira.mira_backend.ai.AIRequest;
import com.mira.mira_backend.service.StyleProfileService;
import com.mira.mira_backend.support.MemoryIntegrationTestSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class MemoryInjectionFailureTest extends MemoryIntegrationTestSupport {

    @MockitoBean
    private StyleProfileService styleProfileService;

    @BeforeEach
    void failProfileLookup() {
        when(styleProfileService.activeStylePrompt(any(UUID.class)))
                .thenThrow(new RuntimeException(
                        "profile lookup failed"));
    }

    @Test
    void chatStillSucceedsWhenTheProfileLookupFails()
            throws Exception {
        LoginTokens user = newUser("inject-failure");
        UUID conversationId = createConversation(
                user.accessToken(),
                "Lookup failure");

        mockAIProvider.setContent("Plain reply");

        var result = sendMessage(
                user.accessToken(),
                conversationId,
                "Hello",
                "lookup-failure-" + UUID.randomUUID());

        assertEquals(201, result.getResponse().getStatus());

        AIRequest request = mockAIProvider.lastRequest();

        assertNotNull(request);
        assertFalse(requestHasSystemContent(
                request,
                "communication-style preferences"));
    }
}