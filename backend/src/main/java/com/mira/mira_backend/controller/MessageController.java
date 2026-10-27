package com.mira.mira_backend.controller;

import com.mira.mira_backend.dto.MessagePageResponse;
import com.mira.mira_backend.dto.SendMessageRequest;
import com.mira.mira_backend.dto.SendMessageResponse;
import com.mira.mira_backend.dto.RetryResponse;

import com.mira.mira_backend.entity.AiResponseStatus;
import com.mira.mira_backend.service.MessageService;
import com.mira.mira_backend.service.MessageRateLimiter;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/conversations/{conversationId}")
public class MessageController {

    private final MessageService messageService;
    private final MessageRateLimiter rateLimiter;

    public MessageController(MessageService messageService, MessageRateLimiter rateLimiter ) {
        this.messageService = messageService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/messages")
    public ResponseEntity<SendMessageResponse> send(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID conversationId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody SendMessageRequest request
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());

        rateLimiter.check(userId);

        SendMessageResponse response = messageService.send(
                userId,
                conversationId,
                request.content(),
                idempotencyKey
        );

        HttpStatus status = response.assistantResponse().status() == AiResponseStatus.UNKNOWN ? HttpStatus.OK : HttpStatus.CREATED;

        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/messages")
    public MessagePageResponse list(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID conversationId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String cursor
    ) {
        return messageService.listMessages(
                UUID.fromString(jwt.getSubject()),
                conversationId,
                limit,
                cursor
        );
    }

    @PostMapping("/responses/{responseId}/retry")
    public ResponseEntity<RetryResponse> retry(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID conversationId,
            @PathVariable UUID responseId,
            @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());

        rateLimiter.check(userId);

        SendMessageResponse result = messageService.retry(
                userId,
                conversationId,
                responseId,
                idempotencyKey
        );

        return ResponseEntity.ok(
                new RetryResponse(result.assistantResponse())
        );
    }
}