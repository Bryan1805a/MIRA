CREATE TABLE refresh_token_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    session_id UUID NOT NULL
        REFERENCES user_session(id)
        ON DELETE CASCADE,

    token_hash VARCHAR(64) NOT NULL,

    issued_at TIMESTAMPTZ NOT NULL,

    consumed_at TIMESTAMPTZ,

    CONSTRAINT uq_refresh_token_history_hash
        UNIQUE (token_hash),

    CONSTRAINT ck_refresh_token_history_consumed
        CHECK (consumed_at IS NULL OR consumed_at >= issued_at)
);

CREATE INDEX idx_refresh_token_history_session
    ON refresh_token_history (session_id);

-- Preserve refresh-token history for sessions that already exist.
INSERT INTO refresh_token_history (
    session_id,
    token_hash,
    issued_at
)
SELECT
    id,
    refresh_token_hash,
    created_at
FROM user_session;
