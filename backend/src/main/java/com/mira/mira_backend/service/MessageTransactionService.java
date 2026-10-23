package com.mira.mira_backend.service;

import com.mira.mira_backend.dto.AssistantResponseResponse;
import com.mira.mira_backend.dto.MessageResponse;
import com.mira.mira_backend.dto.SendMessageResponse;
import com.mira.mira_backend.entity.AiProcessingAttempt;
import com.mira.mira_backend.entity.AiResponse;
import com.mira.mira_backend.entity.AiResponseStatus;
import com.mira.mira_backend.entity.Conversation;
import com.mira.mira_backend.entity.Message;
import com.mira.mira_backend.entity.MessageRole;
import com.mira.mira_backend.entity.UserAccount;
import com.mira.mira_backend.repository.AiProcessingAttemptRepository;
import com.mira.mira_backend.repository.AiResponseRepository;
import com.mira.mira_backend.repository.ConversationRepository;
import com.mira.mira_backend.repository.MessageRepository;
import com.mira.mira_backend.repository.UserAccountRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class MessageTransactionService {

    public static final String SEND_OPERATION = "MESSAGE_SEND";
    private static final int MAX_ATTEMPTS = 3;

    private final UserAccountRepository userAccountRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final AiResponseRepository aiResponseRepository;
    private final AiProcessingAttemptRepository attemptRepository;

    public MessageTransactionService(
            UserAccountRepository userAccountRepository,
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            AiResponseRepository aiResponseRepository,
            AiProcessingAttemptRepository attemptRepository
    ) {
        this.userAccountRepository = userAccountRepository;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.aiResponseRepository = aiResponseRepository;
        this.attemptRepository = attemptRepository;
    }

    public record PendingMessage(
            UUID responseId,
            UUID attemptId,
            MessageResponse userMessage
    ) {
    }

    @Transactional
    public PendingMessage beginSend(
            UUID userId,
            UUID conversationId,
            String content,
            String idempotencyKey,
            String requestHash
    ) {
        UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        Conversation conversation = conversationRepository
                .findOwnedById(conversationId, userId)
                .orElseThrow(ConversationNotFoundException::new);

        Message userMessage = messageRepository.saveAndFlush(
                new Message(
                        conversation,
                        MessageRole.USER,
                        content
                )
        );

        AiResponse response = aiResponseRepository.saveAndFlush(
                new AiResponse(
                        user,
                        conversation,
                        userMessage,
                        SEND_OPERATION,
                        idempotencyKey,
                        requestHash
                )
        );

        AiProcessingAttempt attempt =
                attemptRepository.saveAndFlush(
                        new AiProcessingAttempt(response, 1)
                );

        return new PendingMessage(
                response.getId(),
                attempt.getId(),
                MessageResponse.user(userMessage, response, 1)
        );
    }

    @Transactional
    public PendingMessage beginRetry(
            UUID userId,
            UUID conversationId,
            UUID responseId,
            String idempotencyKey
    ) {
        AiResponse response = lockResponse(
                responseId,
                userId,
                conversationId
        );
    
        if (response.getStatus() != AiResponseStatus.FAILED
                && response.getStatus() != AiResponseStatus.UNKNOWN) {
            throw new InvalidRequestException(
                    "Response is not retryable."
            );
        }
    
        int maxAttempt = attemptRepository
                .findMaxAttemptNumber(responseId);
    
        if (maxAttempt >= MAX_ATTEMPTS) {
            throw new InvalidRequestException(
                    "Retry limit reached."
            );
        }
    
        response.markPending();
    
        AiProcessingAttempt attempt =
                attemptRepository.saveAndFlush(
                        new AiProcessingAttempt(
                                response,
                                maxAttempt + 1,
                                idempotencyKey
                        )
                );
    
        aiResponseRepository.saveAndFlush(response);
    
        return new PendingMessage(
                response.getId(),
                attempt.getId(),
                MessageResponse.user(
                        response.getUserMessage(),
                        response,
                        maxAttempt + 1
                )
        );
    }

    @Transactional
    public SendMessageResponse complete(
            PendingMessage pending,
            UUID userId,
            UUID conversationId,
            String content
    ) {
        AiResponse response = lockResponse(
                pending.responseId(),
                userId,
                conversationId
        );

        if (response.getStatus() != AiResponseStatus.PENDING) {
            throw new InvalidRequestException(
                    "Response is not pending."
            );
        }

        Message assistantMessage = messageRepository.saveAndFlush(
                new Message(
                        response.getConversation(),
                        MessageRole.ASSISTANT,
                        content
                )
        );

        response.markCompleted(assistantMessage);

        AiProcessingAttempt attempt = attemptRepository
                .findByIdAndResponseId(
                        pending.attemptId(),
                        response.getId()
                )
                .orElseThrow(() -> new IllegalStateException(
                        "Attempt not found for response."
                ));

        attempt.succeed();

        aiResponseRepository.saveAndFlush(response);
        attemptRepository.saveAndFlush(attempt);

        return toResult(response);
    }

    @Transactional
    public SendMessageResponse fail(
            PendingMessage pending,
            UUID userId,
            UUID conversationId,
            String errorCategory
    ) {
        return finish(
                pending,
                userId,
                conversationId,
                false,
                errorCategory
        );
    }

    @Transactional
    public SendMessageResponse unknown(
            PendingMessage pending,
            UUID userId,
            UUID conversationId,
            String errorCategory
    ) {
        return finish(
                pending,
                userId,
                conversationId,
                true,
                errorCategory
        );
    }

    @Transactional(readOnly = true)
    public SendMessageResponse currentResult(
            UUID responseId,
            UUID userId,
            UUID conversationId
    ) {
        AiResponse response = aiResponseRepository
                .findByIdAndUserIdAndConversationId(
                        responseId,
                        userId,
                        conversationId
                )
                .orElseThrow(ConversationNotFoundException::new);

        return toResult(response);
    }

    private SendMessageResponse finish(
            PendingMessage pending,
            UUID userId,
            UUID conversationId,
            boolean unknown,
            String errorCategory
    ) {
        AiResponse response = lockResponse(
                pending.responseId(),
                userId,
                conversationId
        );

        if (response.getStatus() != AiResponseStatus.PENDING) {
            throw new InvalidRequestException(
                    "Response is not pending."
            );
        }

        if (unknown) {
            response.markUnknown();
        } else {
            response.markFailed();
        }

        AiProcessingAttempt attempt = attemptRepository
                .findByIdAndResponseId(
                        pending.attemptId(),
                        response.getId()
                )
                .orElseThrow(() -> new IllegalStateException(
                        "Attempt not found for response."
                ));

        if (unknown) {
            attempt.timeout(errorCategory);
        } else {
            attempt.error(errorCategory);
        }

        aiResponseRepository.saveAndFlush(response);
        attemptRepository.saveAndFlush(attempt);

        return toResult(response);
    }

    private AiResponse lockResponse(
            UUID responseId,
            UUID userId,
            UUID conversationId
    ) {
        return aiResponseRepository
                .findOwnedByIdForUpdate(
                        responseId,
                        userId,
                        conversationId
                )
                .orElseThrow(ConversationNotFoundException::new);
    }

    private SendMessageResponse toResult(AiResponse response) {
        int attemptCount = (int) attemptRepository
                .countByResponseId(response.getId());

        MessageResponse userMessage = MessageResponse.user(
                response.getUserMessage(),
                response,
                attemptCount
        );

        AssistantResponseResponse assistantResponse;

        if (response.getStatus() == AiResponseStatus.COMPLETED) {
            Message assistantMessage = response.getAssistantMessage();

            assistantResponse = new AssistantResponseResponse(
                    response.getId(),
                    AiResponseStatus.COMPLETED,
                    assistantMessage.getContent(),
                    response.getCreatedAt()
            );
        } else {
            assistantResponse = new AssistantResponseResponse(
                    response.getId(),
                    response.getStatus(),
                    null,
                    response.getCreatedAt()
            );
        }

        return new SendMessageResponse(
                userMessage,
                assistantResponse
        );
    }
}