package com.mira.mira_backend.service;

import com.mira.mira_backend.dto.ConversationPageResponse;
import com.mira.mira_backend.dto.ConversationResponse;
import com.mira.mira_backend.entity.Conversation;
import com.mira.mira_backend.entity.UserAccount;
import com.mira.mira_backend.repository.ConversationRepository;
import com.mira.mira_backend.repository.UserAccountRepository;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ConversationService {

    private static final int MAX_LIMIT = 50;

    private final ConversationRepository conversationRepository;
    private final UserAccountRepository userAccountRepository;

    public ConversationService(
            ConversationRepository conversationRepository,
            UserAccountRepository userAccountRepository
    ) {
        this.conversationRepository = conversationRepository;
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional
    public ConversationResponse create(UUID userId, String title) {
        UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        Conversation conversation = new Conversation(
                user,
                normalizeTitle(title)
        );

        return ConversationResponse.from(
                conversationRepository.save(conversation)
        );
    }

    @Transactional(readOnly = true)
    public ConversationPageResponse list(
            UUID userId,
            int limit,
            String cursor
    ) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new InvalidRequestException(
                    "limit must be between 1 and " + MAX_LIMIT + "."
            );
        }

        int querySize = limit + 1;
        List<Conversation> rows;

        if (cursor == null || cursor.isBlank()) {
            rows = conversationRepository.findFirstPage(
                    userId,
                    PageRequest.of(0, querySize)
            );
        } else {
            ConversationCursor.Cursor decoded =
                    ConversationCursor.decode(cursor);

            rows = conversationRepository.findPageAfter(
                    userId,
                    decoded.updatedAt(),
                    decoded.id(),
                    PageRequest.of(0, querySize)
            );
        }

        boolean hasMore = rows.size() > limit;

        List<Conversation> pageRows = hasMore
                ? rows.subList(0, limit)
                : rows;

        String nextCursor = null;

        if (hasMore) {
            Conversation last = pageRows.get(pageRows.size() - 1);

            nextCursor = ConversationCursor.encode(
                    last.getUpdatedAt(),
                    last.getId()
            );
        }

        return new ConversationPageResponse(
                pageRows.stream()
                        .map(ConversationResponse::from)
                        .toList(),
                nextCursor
        );
    }

    @Transactional
    public void delete(UUID userId, UUID conversationId) {
        Conversation conversation = conversationRepository
                .findOwnedById(conversationId, userId)
                .orElseThrow(ConversationNotFoundException::new);

        conversationRepository.delete(conversation);
    }

    private String normalizeTitle(String title) {
        if (title == null) {
            return null;
        }

        String trimmed = title.trim();

        return trimmed.isEmpty() ? null : trimmed;
    }
}