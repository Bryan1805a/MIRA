ALTER TABLE ai_processing_attempt
    ADD COLUMN idempotency_key VARCHAR(128);

CREATE UNIQUE INDEX uq_ai_attempt_idempotency
    ON ai_processing_attempt (response_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;