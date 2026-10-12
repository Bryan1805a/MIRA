CREATE TABLE conversation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    user_id UUID NOT NULL
        REFERENCES user_account(id) ON DELETE CASCADE,

    title VARCHAR(200),

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_conversation_id_user
        UNIQUE (id, user_id)
);

CREATE INDEX idx_conversation_user_updated
    ON conversation (user_id, updated_at DESC);


CREATE TABLE message (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    conversation_id UUID NOT NULL
        REFERENCES conversation(id) ON DELETE CASCADE,

    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_message_role
        CHECK (role IN ('USER', 'ASSISTANT')),

    CONSTRAINT ck_message_content_nonempty
        CHECK (LENGTH(TRIM(content)) > 0),

    CONSTRAINT uq_message_id_conversation
        UNIQUE (id, conversation_id)
);

CREATE INDEX idx_message_conversation_created
    ON message (conversation_id, created_at ASC, id ASC);