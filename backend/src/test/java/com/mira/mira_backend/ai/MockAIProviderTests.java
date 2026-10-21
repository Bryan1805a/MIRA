package com.mira.mira_backend.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MockAIProviderTests {

    private final MockAIProvider provider = new MockAIProvider();

    private final AIRequest request = new AIRequest(
            List.of(new AIMessage(AIRole.USER, "Hello"))
    );

    @Test
    void returnsConfiguredContentOnSuccess() {
        provider.setContent("A mock reply.");

        AICompletion completion = provider.generate(request);

        assertEquals("A mock reply.", completion.content());
    }

    @Test
    void timeoutRaisesTimeoutCategory() {
        provider.setScenario(MockAIProvider.Scenario.TIMEOUT);

        AIProviderException exception = assertThrows(
                AIProviderException.class,
                () -> provider.generate(request)
        );

        assertEquals(AIErrorCategory.TIMEOUT, exception.category());
    }

    @Test
    void rateLimitRaisesRateLimitedCategory() {
        provider.setScenario(MockAIProvider.Scenario.RATE_LIMITED);

        AIProviderException exception = assertThrows(
                AIProviderException.class,
                () -> provider.generate(request)
        );

        assertEquals(
                AIErrorCategory.RATE_LIMITED,
                exception.category()
        );
    }

    @Test
    void unavailableRaisesUnavailableCategory() {
        provider.setScenario(MockAIProvider.Scenario.UNAVAILABLE);

        AIProviderException exception = assertThrows(
                AIProviderException.class,
                () -> provider.generate(request)
        );

        assertEquals(
                AIErrorCategory.UNAVAILABLE,
                exception.category()
        );
    }

    @Test
    void invalidOutputRaisesInvalidOutputCategory() {
        provider.setScenario(MockAIProvider.Scenario.INVALID_OUTPUT);

        AIProviderException exception = assertThrows(
                AIProviderException.class,
                () -> provider.generate(request)
        );

        assertEquals(
                AIErrorCategory.INVALID_OUTPUT,
                exception.category()
        );
    }
}