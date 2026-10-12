CREATE TABLE ai_response (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    user_id UUID NOT NULL,
    conversation_id UUID NOT NULL,
    user_message_id UUID NOT NULL,

    assistant_message_id UUID,

    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',

    operation VARCHAR(40) NOT NULL DEFAULT 'MESSAGE_SEND',
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_ai_response_conversation_user
        FOREIGN KEY (conversation_id, user_id)
        REFERENCES conversation (id, user_id)
        ON DELETE CASCADE,

    CONSTRAINT fk_ai_response_user_message
        FOREIGN KEY (user_message_id, conversation_id)
        REFERENCES message (id, conversation_id)
        ON DELETE CASCADE,

    CONSTRAINT fk_ai_response_assistant_message
        FOREIGN KEY (assistant_message_id, conversation_id)
        REFERENCES message (id, conversation_id)
        ON DELETE CASCADE,

    CONSTRAINT ck_ai_response_status
        CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'UNKNOWN')),

    CONSTRAINT ck_ai_response_assistant_message
        CHECK (
            (status = 'COMPLETED' AND assistant_message_id IS NOT NULL)
            OR
            (status <> 'COMPLETED' AND assistant_message_id IS NULL)
        ),

    CONSTRAINT uq_ai_response_user_message
        UNIQUE (user_message_id),

    CONSTRAINT uq_ai_response_idempotency
        UNIQUE (user_id, operation, idempotency_key)
);

CREATE INDEX idx_ai_response_conversation_created
    ON ai_response (conversation_id, created_at ASC);


CREATE TABLE ai_processing_attempt (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    response_id UUID NOT NULL
        REFERENCES ai_response(id) ON DELETE CASCADE,

    attempt_number INTEGER NOT NULL,
    outcome VARCHAR(20) NOT NULL,

    error_category VARCHAR(50),

    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMPTZ,

    CONSTRAINT ck_ai_attempt_number
        CHECK (attempt_number >= 1),

    CONSTRAINT ck_ai_attempt_outcome
        CHECK (
            outcome IN ('IN_PROGRESS', 'SUCCESS', 'TIMEOUT', 'ERROR')
        ),

    CONSTRAINT ck_ai_attempt_finished
        CHECK (
            (outcome = 'IN_PROGRESS' AND finished_at IS NULL)
            OR
            (outcome <> 'IN_PROGRESS' AND finished_at IS NOT NULL)
        ),

    CONSTRAINT uq_ai_attempt_number
        UNIQUE (response_id, attempt_number)
);

CREATE INDEX idx_ai_attempt_response_number
    ON ai_processing_attempt (response_id, attempt_number ASC);