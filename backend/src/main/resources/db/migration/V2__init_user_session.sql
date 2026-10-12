CREATE TABLE user_session (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    user_id UUID NOT NULL
        REFERENCES user_account(id) ON DELETE CASCADE,

    refresh_token_hash VARCHAR(64) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    last_used_at TIMESTAMPTZ,

    CONSTRAINT ck_user_session_expiry
        CHECK (expires_at > created_at)
);

CREATE INDEX idx_user_session_user_id
    ON user_session (user_id);

CREATE INDEX idx_user_session_token_hash
    ON user_session (refresh_token_hash);

CREATE INDEX idx_user_session_active
    ON user_session (user_id)
    WHERE revoked_at IS NULL;