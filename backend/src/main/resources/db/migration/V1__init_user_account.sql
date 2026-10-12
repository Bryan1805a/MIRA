CREATE TABLE user_account (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uq_user_account_email_normalized
    ON user_account (LOWER(TRIM(email)));