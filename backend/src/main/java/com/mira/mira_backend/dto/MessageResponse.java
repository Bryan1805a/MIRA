package com.mira.mira_backend.dto;

import com.mira.mira_backend.entity.AiResponse;
import com.mira.mira_backend.entity.AiResponseStatus;
import com.mira.mira_backend.entity.Message;
import com.mira.mira_backend.entity.MessageRole;

import java.time.Instant;
import java.util.UUID;

public record MessageResponse(
        UUID id,
        MessageRole role,
        String content,
        Instant createdAt,
        ResponseMetadata response
) {
    public static MessageResponse assistant(Message message) {
        return new MessageResponse(
                message.getId(),
                message.getRole(),
                message.getContent(),
                message.getCreatedAt(),
                null
        );
    }

    public static MessageResponse user(
            Message message,
            AiResponse response,
            int attemptCount
    ) {
        ResponseMetadata metadata = response == null
                ? null
                : new ResponseMetadata(
                        response.getId(),
                        response.getStatus(),
                        attemptCount
                );

        return new MessageResponse(
                message.getId(),
                message.getRole(),
                message.getContent(),
                message.getCreatedAt(),
                metadata
        );
    }

    public record ResponseMetadata(
            UUID id,
            AiResponseStatus status,
            int attemptCount
    ) {
    }
}