package com.mira.mira_backend.ai;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import tools.jackson.databind.JsonNode;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.LinkedHashMap;

@Component
@Profile("!test")
public class DeepSeekAIProvider implements AIProvider {

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 30_000;

    private final RestClient restClient;
    private final String model;

    @Autowired
    public DeepSeekAIProvider(
            RestClient.Builder restClientBuilder,
            @Value("${mira.ai.deepseek.api-key:}") String apiKey,
            @Value("${mira.ai.deepseek.base-url:https://api.deepseek.com}")
            String baseUrl,
            @Value("${mira.ai.deepseek.model:deepseek-chat}")
            String model
    ) {
        this(restClientBuilder, null, apiKey, baseUrl, model);
    }

    public DeepSeekAIProvider(
            RestClient.Builder restClientBuilder,
            ClientHttpRequestFactory customRequestFactory,
            String apiKey,
            String baseUrl,
            String model
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "mira.ai.deepseek.api-key (DEEPSEEK_API_KEY) "
                            + "must be set when the DeepSeek provider is active."
            );
        }

        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException(
                    "mira.ai.deepseek.base-url must not be blank."
            );
        }

        if (model == null || model.isBlank()) {
            throw new IllegalStateException(
                    "mira.ai.deepseek.model must not be blank."
            );
        }

        String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;

        if (customRequestFactory != null) {
            restClientBuilder.requestFactory(customRequestFactory);
        }
        else if (!hasCustomRequestFactory(restClientBuilder)) {
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(CONNECT_TIMEOUT_MS);
            requestFactory.setReadTimeout(READ_TIMEOUT_MS);
            restClientBuilder.requestFactory(requestFactory);
        }

        this.restClient = restClientBuilder.baseUrl(normalizedBaseUrl).defaultHeader(
                                                                            HttpHeaders.AUTHORIZATION,
                                                                            "Bearer " + apiKey
                                                                       )
                                                                       .defaultHeader(
                                                                            HttpHeaders.CONTENT_TYPE,
                                                                            MediaType.APPLICATION_JSON_VALUE
                                                                       )
                                                                       .build();

        this.model = model;
    }

    private static boolean hasCustomRequestFactory(RestClient.Builder builder) {
        try {
            Field field = builder.getClass().getDeclaredField("requestFactory");
            field.setAccessible(true);
            return field.get(builder) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public AICompletion generate(AIRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        List<Map<String, String>> messages = request.messages()
                .stream()
                .map(message -> Map.of(
                        "role",
                        message.role()
                                .name()
                                .toLowerCase(Locale.ROOT),
                        "content",
                        message.content()
                ))
                .toList();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("stream", false);

        if (request.jsonResponse()) {
            body.put(
                    "response_format",
                    Map.of("type", "json_object")
            );
        }

        try {
            JsonNode response = restClient.post()
                    .uri("/chat/completions")
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            return parseCompletion(response);
        } catch (AIProviderException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            throw new AIProviderException(
                    AIErrorCategory.TIMEOUT,
                    "DeepSeek request timed out.",
                    exception
            );
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();

            if (status == 429) {
                throw new AIProviderException(
                        AIErrorCategory.RATE_LIMITED,
                        "DeepSeek rate limit reached.",
                        exception
                );
            }

            if (status >= 500) {
                throw new AIProviderException(
                        AIErrorCategory.UNAVAILABLE,
                        "DeepSeek is currently unavailable.",
                        exception
                );
            }

            throw new AIProviderException(
                    AIErrorCategory.PROVIDER_ERROR,
                    "DeepSeek rejected the request.",
                    exception
            );
        } catch (RestClientException exception) {
            throw new AIProviderException(
                    AIErrorCategory.UNAVAILABLE,
                    "The DeepSeek request failed.",
                    exception
            );
        } catch (RuntimeException exception) {
            throw new AIProviderException(
                    AIErrorCategory.INVALID_OUTPUT,
                    "DeepSeek returned an unusable response.",
                    exception
            );
        }
    }

    private AICompletion parseCompletion(JsonNode root) {
        if (root == null) {
            throw new AIProviderException(
                    AIErrorCategory.INVALID_OUTPUT,
                    "DeepSeek returned an empty response."
            );
        }

        JsonNode choices = root.get("choices");

        if (choices == null
                || !choices.isArray()
                || choices.size() == 0) {
            throw new AIProviderException(
                    AIErrorCategory.INVALID_OUTPUT,
                    "DeepSeek response did not contain any choices."
            );
        }

        JsonNode content = choices.get(0)
                .path("message")
                .path("content");

        if (!content.isString() || content.asString().isBlank()) {
            throw new AIProviderException(
                    AIErrorCategory.INVALID_OUTPUT,
                    "DeepSeek response did not contain assistant content."
            );
        }

        return new AICompletion(content.asString());
    }
}
