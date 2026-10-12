CREATE TABLE user_style_profile (
    user_id UUID PRIMARY KEY
        REFERENCES user_account(id) ON DELETE CASCADE,

    profile JSONB NOT NULL DEFAULT '{}'::jsonb,

    version INTEGER NOT NULL DEFAULT 1,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,

    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_user_style_profile_object
        CHECK (jsonb_typeof(profile) = 'object'),

    CONSTRAINT ck_user_style_profile_version
        CHECK (version >= 1)
);