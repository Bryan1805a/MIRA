CREATE TABLE memory_extraction_job (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    user_id UUID NOT NULL
        REFERENCES user_account(id) ON DELETE CASCADE,

    conversation_id UUID NOT NULL
        REFERENCES conversation(id) ON DELETE CASCADE,

    response_id UUID NOT NULL UNIQUE
        REFERENCES ai_response(id) ON DELETE CASCADE,

    expected_generation INTEGER NOT NULL,
    expected_version INTEGER NOT NULL,

    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    error_category VARCHAR(50),

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,

    CONSTRAINT ck_memory_job_generation
        CHECK (expected_generation >= 1),

    CONSTRAINT ck_memory_job_version
        CHECK (expected_version >= 1),

    CONSTRAINT ck_memory_job_attempts
        CHECK (attempts >= 0),

    CONSTRAINT ck_memory_job_status
        CHECK (
            status IN (
                'PENDING',
                'PROCESSING',
                'COMPLETED',
                'SKIPPED',
                'FAILED'
            )
        )
);

CREATE INDEX idx_memory_job_status_created
    ON memory_extraction_job (status, created_at ASC);


CREATE TABLE user_memory_candidate (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    user_id UUID NOT NULL
        REFERENCES user_account(id) ON DELETE CASCADE,

    field VARCHAR(50) NOT NULL,
    value VARCHAR(50) NOT NULL,
    classification VARCHAR(30) NOT NULL,
    evidence TEXT,

    evidence_count INTEGER NOT NULL DEFAULT 1,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    generation INTEGER NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_memory_candidate_classification
        CHECK (
            classification IN (
                'INFERRED',
                'EXPLICIT_DURABLE',
                'EXPLICIT_CORRECTION',
                'EPHEMERAL'
            )
        ),

    CONSTRAINT ck_memory_candidate_status
        CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED')),

    CONSTRAINT ck_memory_candidate_evidence_count
        CHECK (evidence_count >= 1),

    CONSTRAINT ck_memory_candidate_generation
        CHECK (generation >= 1)
);

CREATE UNIQUE INDEX uq_memory_candidate_pending
    ON user_memory_candidate (user_id, field, value)
    WHERE status = 'PENDING';

CREATE INDEX idx_memory_candidate_user_status
    ON user_memory_candidate (user_id, status, created_at DESC);