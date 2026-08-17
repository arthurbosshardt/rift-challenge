CREATE TABLE user_riot_account (
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    riot_puuid      VARCHAR(78) NOT NULL,
    riot_game_name  VARCHAR(16) NOT NULL,
    riot_tag_line   VARCHAR(5) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_user_riot_account_user_puuid UNIQUE (user_id, riot_puuid),
    CONSTRAINT uq_user_riot_account_puuid UNIQUE (riot_puuid)
);

CREATE INDEX idx_user_riot_account_user_id ON user_riot_account (user_id);
