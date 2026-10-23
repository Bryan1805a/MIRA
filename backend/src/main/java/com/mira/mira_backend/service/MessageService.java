package com.mira.mira_backend.service;

import com.mira.mira_backend.ai.AICompletion;
import com.mira.mira_backend.ai.AIErrorCategory;
import com.mira.mira_backend.ai.AIMessage;
import com.mira.mira_backend.ai.AIProvider;
import com.mira.mira_backend.ai.AIProviderException;
import com.mira.mira_backend.ai.AIRequest;
import com.mira.mira_backend.ai.AIRole;

import com.mira.mira_backend.dto.MessagePageResponse;
import com.mira.mira_backend.dto.MessageResponse;
import com.mira.mira_backend.dto.SendMessageResponse;

import com.mira.mira_backend.entity.AiResponse;
import com.mira.mira_backend.entity.Message;
import com.mira.mira_backend.entity.MessageRole;
import com.mira.mira_backend.entity.AiProcessingAttempt;

import com.mira.mira_backend.repository.AiProcessingAttemptRepository;
import com.mira.mira_backend.repository.AiResponseRepository;
import com.mira.mira_backend.repository.ConversationRepository;
import com.mira.mira_backend.repository.MessageRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class MessageService {

    private static final int MAX_CONTEXT_MESSAGES = 10;
    private static final int MAX_MESSAGE_LIMIT = 100;

    private static final String SYSTEM_PROMPT = """
            You are MIRA, a helpful conversational assistant.
            Follow the user's current instructions over any prior preference.
            Be accurate, clear, and concise.
            """;

    private final MessageTransactionService transactionService;
    private final MessageRepository messageRepository;
    private final AiResponseRepository aiResponseRepository;
    private final AiProcessingAttemptRepository attemptRepository;
    private final ConversationRepository conversationRepository;
    private final AIProvider aiProvider;

    public MessageService(
            MessageTransactionService transactionService,
            MessageRepository messageRepository,
            AiResponseRepository aiResponseRepository,
            AiProcessingAttemptRepository attemptRepository,
            ConversationRepository conversationRepository,
            AIProvider aiProvider
    ) {
        this.transactionService = transactionService;
        this.messageRepository = messageRepository;
        this.aiResponseRepository = aiResponseRepository;
        this.attemptRepository = attemptRepository;
        this.conversationRepository = conversationRepository;
        this.aiProvider = aiProvider;
    }

    public SendMessageResponse send(
            UUID userId,
            UUID conversationId,
            String content,
            String idempotencyKey
    ) {
        validateIdempotencyKey(idempotencyKey);

        String normalizedContent = content.trim();

        String requestHash = RequestHash.sha256(
                conversationId + "|" + normalizedContent
        );

        Optional<AiResponse> existing = aiResponseRepository
                .findByUserIdAndOperationAndIdempotencyKey(
                        userId,
                        MessageTransactionService.SEND_OPERATION,
                        idempotencyKey
                );

        if (existing.isPresent()) {
            return replay(
                    existing.get(),
                    userId,
                    conversationId,
                    requestHash
            );
        }

        MessageTransactionService.PendingMessage pending;

        try {
            pending = transactionService.beginSend(
                    userId,
                    conversationId,
                    normalizedContent,
                    idempotencyKey,
                    requestHash
            );
        } catch (DataIntegrityViolationException exception) {
            AiResponse concurrent = aiResponseRepository
                    .findByUserIdAndOperationAndIdempotencyKey(
                            userId,
                            MessageTransactionService.SEND_OPERATION,
                            idempotencyKey
                    )
                    .orElseThrow(() -> exception);

            return replay(
                    concurrent,
                    userId,
                    conversationId,
                    requestHash
            );
        }

        return generateAndFinish(
                pending,
                userId,
                conversationId
        );
    }

    public SendMessageResponse retry(
        UUID userId,
        UUID conversationId,
        UUID responseId,
        String idempotencyKey
    ) {
        validateIdempotencyKey(idempotencyKey);
    
        aiResponseRepository
                .findByIdAndUserIdAndConversationId(
                        responseId,
                        userId,
                        conversationId
                )
                .orElseThrow(ConversationNotFoundException::new);
    
        Optional<AiProcessingAttempt> existingAttempt =
                attemptRepository.findByResponseIdAndIdempotencyKey(
                        responseId,
                        idempotencyKey
                );
    
        if (existingAttempt.isPresent()) {
            return transactionService.currentResult(
                    responseId,
                    userId,
                    conversationId
            );
        }
    
        MessageTransactionService.PendingMessage pending;
    
        try {
            pending = transactionService.beginRetry(
                    userId,
                    conversationId,
                    responseId,
                    idempotencyKey
            );
        } catch (DataIntegrityViolationException
                 | InvalidRequestException exception) {
            /*
             * A concurrent retry may have committed the same idempotency key
             * after our lookup. If it did, return the existing result.
             * Otherwise the original failure is real.
             */
            if (attemptRepository
                    .findByResponseIdAndIdempotencyKey(
                            responseId,
                            idempotencyKey
                    )
                    .isPresent()) {
                return transactionService.currentResult(
                        responseId,
                        userId,
                        conversationId
                );
            }
    
            throw exception;
        }
    
        return generateAndFinish(
                pending,
                userId,
                conversationId
        );
    }

    @Transactional(readOnly = true)
    public MessagePageResponse listMessages(
            UUID userId,
            UUID conversationId,
            int limit,
            String cursor
    ) {
        if (limit < 1 || limit > MAX_MESSAGE_LIMIT) {
            throw new InvalidRequestException(
                    "limit must be between 1 and "
                            + MAX_MESSAGE_LIMIT + "."
            );
        }

        conversationRepository
                .findOwnedById(conversationId, userId)
                .orElseThrow(ConversationNotFoundException::new);

        int querySize = limit + 1;
        List<Message> rows;

        if (cursor == null || cursor.isBlank()) {
            rows = messageRepository.findFirstPage(
                    conversationId,
                    userId,
                    PageRequest.of(0, querySize)
            );
        } else {
            MessageCursor.Cursor decoded =
                    MessageCursor.decode(cursor);

            rows = messageRepository.findPageAfter(
                    conversationId,
                    userId,
                    decoded.createdAt(),
                    decoded.id(),
                    PageRequest.of(0, querySize)
            );
        }

        boolean hasMore = rows.size() > limit;

        List<Message> pageRows = hasMore
                ? rows.subList(0, limit)
                : rows;

        String nextCursor = null;

        if (hasMore) {
            Message last = pageRows.get(pageRows.size() - 1);

            nextCursor = MessageCursor.encode(
                    last.getCreatedAt(),
                    last.getId()
            );
        }

        return new MessagePageResponse(
                pageRows.stream()
                        .map(this::toMessageResponse)
                        .toList(),
                nextCursor
        );
    }

    private SendMessageResponse replay(
            AiResponse existing,
            UUID userId,
            UUID conversationId,
            String requestHash
    ) {
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new IdempotencyConflictException();
        }

        return transactionService.currentResult(
                existing.getId(),
                userId,
                conversationId
        );
    }

    private SendMessageResponse generateAndFinish(
            MessageTransactionService.PendingMessage pending,
            UUID userId,
            UUID conversationId
    ) {
        AIRequest request = buildRequest(userId, conversationId);
        AICompletion completion;

        try {
            completion = aiProvider.generate(request);
        } catch (AIProviderException exception) {
            if (exception.category() == AIErrorCategory.TIMEOUT) {
                return transactionService.unknown(
                        pending,
                        userId,
                        conversationId,
                        exception.category().name()
                );
            }

            return transactionService.fail(
                    pending,
                    userId,
                    conversationId,
                    exception.category().name()
            );
        } catch (RuntimeException exception) {
            return transactionService.unknown(
                    pending,
                    userId,
                    conversationId,
                    "UNEXPECTED"
            );
        }

        return transactionService.complete(
                pending,
                userId,
                conversationId,
                completion.content()
        );
    }

    private AIRequest buildRequest(
            UUID userId,
            UUID conversationId
    ) {
        List<Message> recent = messageRepository.findRecent(
                conversationId,
                userId,
                PageRequest.of(0, MAX_CONTEXT_MESSAGES)
        );

        List<Message> chronological = new ArrayList<>(recent);
        Collections.reverse(chronological);

        List<AIMessage> messages = new ArrayList<>(
                chronological.size() + 1
        );

        messages.add(new AIMessage(AIRole.SYSTEM, SYSTEM_PROMPT));

        for (Message message : chronological) {
            AIRole role = message.getRole() == MessageRole.USER
                    ? AIRole.USER
                    : AIRole.ASSISTANT;

            messages.add(new AIMessage(role, message.getContent()));
        }

        return new AIRequest(messages);
    }

    private MessageResponse toMessageResponse(Message message) {
        if (message.getRole() == MessageRole.ASSISTANT) {
            return MessageResponse.assistant(message);
        }

        AiResponse response = aiResponseRepository
                .findByUserMessageId(message.getId())
                .orElse(null);

        if (response == null) {
            return MessageResponse.user(message, null, 0);
        }

        int attemptCount = (int) attemptRepository
                .countByResponseId(response.getId());

        return MessageResponse.user(
                message,
                response,
                attemptCount
        );
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null
                || idempotencyKey.isBlank()
                || idempotencyKey.length() > 128) {
            throw new InvalidRequestException(
                    "Idempotency-Key must be between 1 and 128 characters."
            );
        }
    }
}