CREATE TABLE user_memory_control (
    user_id UUID PRIMARY KEY
        REFERENCES user_account(id) ON DELETE CASCADE,

    generation INTEGER NOT NULL DEFAULT 1,

    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_user_memory_control_generation
        CHECK (generation >= 1)
);