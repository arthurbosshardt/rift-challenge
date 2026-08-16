CREATE TABLE app_user (
    id              UUID PRIMARY KEY,
    email           VARCHAR(320) NOT NULL,
    username        VARCHAR(64) NOT NULL,
    password_hash   VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_app_user_email UNIQUE (email),
    CONSTRAINT uq_app_user_username UNIQUE (username)
);

CREATE TABLE race (
    id              UUID PRIMARY KEY,
    owner_id        UUID NOT NULL REFERENCES app_user (id) ON DELETE RESTRICT,
    name            VARCHAR(120) NOT NULL,
    type            VARCHAR(16) NOT NULL,
    start_at        TIMESTAMPTZ NOT NULL,
    is_public       BOOLEAN NOT NULL DEFAULT FALSE,
    share_slug      UUID NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_race_share_slug UNIQUE (share_slug),
    CONSTRAINT chk_race_type CHECK (type IN ('SOLOQ', 'DUOQ'))
);

CREATE INDEX idx_race_owner_id ON race (owner_id);
CREATE INDEX idx_race_is_public ON race (is_public) WHERE is_public = TRUE;
CREATE INDEX idx_race_start_at ON race (start_at);

CREATE TABLE race_participant (
    id              UUID PRIMARY KEY,
    race_id         UUID NOT NULL REFERENCES race (id) ON DELETE CASCADE,
    riot_puuid      VARCHAR(78),
    riot_game_name  VARCHAR(16),
    riot_tag_line   VARCHAR(5),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_race_participant_race_id ON race_participant (race_id);

CREATE TABLE race_refresh (
    id              UUID PRIMARY KEY,
    race_id         UUID NOT NULL REFERENCES race (id) ON DELETE CASCADE,
    refreshed_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_race_refresh_race_id UNIQUE (race_id)
);
