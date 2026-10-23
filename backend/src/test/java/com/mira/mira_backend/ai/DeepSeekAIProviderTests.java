package com.mira.mira_backend.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DeepSeekAIProviderTests {

    private MockRestServiceServer server;
    private DeepSeekAIProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();

        provider = new DeepSeekAIProvider(
                builder,
                "test-key",
                "https://api.deepseek.test",
                "deepseek-chat"
        );
    }

    @Test
    void sendsOpenAiCompatibleRequestAndParsesCompletion() {
        server.expect(requestTo(
                        "https://api.deepseek.test/chat/completions"
                ))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer test-key"
                ))
                .andExpect(content().json("""
                        {
                          "model": "deepseek-chat",
                          "stream": false,
                          "messages": [
                            {
                              "role": "system",
                              "content": "You are helpful."
                            },
                            {
                              "role": "user",
                              "content": "Hello"
                            }
                          ]
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "choices": [
                            {
                              "message": {
                                "role": "assistant",
                                "content": "Hi!"
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        AICompletion completion = provider.generate(
                new AIRequest(List.of(
                        new AIMessage(AIRole.SYSTEM, "You are helpful."),
                        new AIMessage(AIRole.USER, "Hello")
                ))
        );

        assertEquals("Hi!", completion.content());
        server.verify();
    }

    @Test
    void mapsTooManyRequestsToRateLimited() {
        server.expect(requestTo(
                        "https://api.deepseek.test/chat/completions"
                ))
                .andRespond(withStatus(
                        HttpStatus.TOO_MANY_REQUESTS
                ));

        AIProviderException exception = assertThrows(
                AIProviderException.class,
                () -> provider.generate(singleUserRequest())
        );

        assertEquals(
                AIErrorCategory.RATE_LIMITED,
                exception.category()
        );
        server.verify();
    }

    @Test
    void mapsServerErrorToUnavailable() {
        server.expect(requestTo(
                        "https://api.deepseek.test/chat/completions"
                ))
                .andRespond(withStatus(
                        HttpStatus.INTERNAL_SERVER_ERROR
                ));

        AIProviderException exception = assertThrows(
                AIProviderException.class,
                () -> provider.generate(singleUserRequest())
        );

        assertEquals(
                AIErrorCategory.UNAVAILABLE,
                exception.category()
        );
        server.verify();
    }

    @Test
    void mapsOtherClientErrorToProviderError() {
        server.expect(requestTo(
                        "https://api.deepseek.test/chat/completions"
                ))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        AIProviderException exception = assertThrows(
                AIProviderException.class,
                () -> provider.generate(singleUserRequest())
        );

        assertEquals(
                AIErrorCategory.PROVIDER_ERROR,
                exception.category()
        );
        server.verify();
    }

    @Test
    void mapsMissingChoicesToInvalidOutput() {
        server.expect(requestTo(
                        "https://api.deepseek.test/chat/completions"
                ))
                .andRespond(withSuccess(
                        "{}",
                        MediaType.APPLICATION_JSON
                ));

        AIProviderException exception = assertThrows(
                AIProviderException.class,
                () -> provider.generate(singleUserRequest())
        );

        assertEquals(
                AIErrorCategory.INVALID_OUTPUT,
                exception.category()
        );
        server.verify();
    }

    @Test
    void mapsBlankAssistantContentToInvalidOutput() {
        server.expect(requestTo(
                        "https://api.deepseek.test/chat/completions"
                ))
                .andRespond(withSuccess("""
                        {
                          "choices": [
                            {
                              "message": {
                                "role": "assistant",
                                "content": "   "
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        AIProviderException exception = assertThrows(
                AIProviderException.class,
                () -> provider.generate(singleUserRequest())
        );

        assertEquals(
                AIErrorCategory.INVALID_OUTPUT,
                exception.category()
        );
        server.verify();
    }

    private AIRequest singleUserRequest() {
        return new AIRequest(List.of(
                new AIMessage(AIRole.USER, "Hello")
        ));
    }
}