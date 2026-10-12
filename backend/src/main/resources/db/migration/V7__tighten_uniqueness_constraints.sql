-- A refresh token hash must identify at most one session.
CREATE UNIQUE INDEX uq_user_session_token_hash
    ON user_session (refresh_token_hash);

-- An assistant message may belong to at most one AI response.
CREATE UNIQUE INDEX uq_ai_response_assistant_message
    ON ai_response (assistant_message_id)
    WHERE assistant_message_id IS NOT NULL;

-- Remove the non-unique index duplicated by uq_ai_attempt_number.
DROP INDEX idx_ai_attempt_response_number;