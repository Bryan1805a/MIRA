package com.mira.mira_backend.ai;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@Profile("test")
public class MockAIProvider implements AIProvider {

    public enum Scenario {
        SUCCESS,
        TIMEOUT,
        RATE_LIMITED,
        UNAVAILABLE,
        INVALID_OUTPUT
    }

    private volatile Scenario scenario = Scenario.SUCCESS;
    private volatile String content = "Mock assistant response.";

    public void setScenario(Scenario scenario) {
        this.scenario = Objects.requireNonNull(
                scenario,
                "scenario must not be null"
        );
    }

    public void setContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException(
                    "content must not be blank"
            );
        }

        this.content = content;
    }

    public void reset() {
        scenario = Scenario.SUCCESS;
        content = "Mock assistant response.";
    }

    @Override
    public AICompletion generate(AIRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        return switch (scenario) {
            case SUCCESS -> new AICompletion(content);
            case TIMEOUT -> throw new AIProviderException(
                    AIErrorCategory.TIMEOUT,
                    "Mock provider timeout."
            );
            case RATE_LIMITED -> throw new AIProviderException(
                    AIErrorCategory.RATE_LIMITED,
                    "Mock provider rate limit."
            );
            case UNAVAILABLE -> throw new AIProviderException(
                    AIErrorCategory.UNAVAILABLE,
                    "Mock provider unavailable."
            );
            case INVALID_OUTPUT -> throw new AIProviderException(
                    AIErrorCategory.INVALID_OUTPUT,
                    "Mock provider returned invalid output."
            );
        };
    }
}