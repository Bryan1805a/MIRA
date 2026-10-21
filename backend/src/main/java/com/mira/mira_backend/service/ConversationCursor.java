package com.mira.mira_backend.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

final class ConversationCursor {

    private static final String SEPARATOR = "|";
    private static final int MAX_ENCODED_LENGTH = 256;

    private ConversationCursor() {
    }

    static String encode(Instant updatedAt, UUID id) {
        String raw = updatedAt + SEPARATOR + id;

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    static Cursor decode(String encoded) {
        if (encoded == null
                || encoded.isBlank()
                || encoded.length() > MAX_ENCODED_LENGTH) {
            throw new InvalidRequestException("Invalid cursor.");
        }

        try {
            byte[] bytes = Base64.getUrlDecoder().decode(encoded);
            String raw = new String(bytes, StandardCharsets.UTF_8);

            int separatorIndex = raw.indexOf(SEPARATOR);

            if (separatorIndex <= 0
                    || separatorIndex == raw.length() - 1) {
                throw new InvalidRequestException("Invalid cursor.");
            }

            return new Cursor(
                    Instant.parse(raw.substring(0, separatorIndex)),
                    UUID.fromString(raw.substring(separatorIndex + 1))
            );
        } catch (InvalidRequestException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new InvalidRequestException("Invalid cursor.");
        }
    }

    record Cursor(Instant updatedAt, UUID id) {
    }
}