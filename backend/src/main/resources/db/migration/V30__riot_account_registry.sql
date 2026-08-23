-- Riot identities tracked independently of app-user links. Match/rank rows stay keyed by PUUID.

CREATE TABLE riot_account (
    id                              UUID PRIMARY KEY,
    riot_puuid                      VARCHAR(78) NOT NULL,
    riot_game_name                  VARCHAR(16) NOT NULL,
    riot_tag_line                   VARCHAR(5) NOT NULL,
    profile_icon_id                 INT,
    activity_season_history_exhausted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_riot_account_puuid UNIQUE (riot_puuid)
);

CREATE INDEX idx_riot_account_game_name ON riot_account (riot_game_name);

INSERT INTO riot_account (
    id,
    riot_puuid,
    riot_game_name,
    riot_tag_line,
    profile_icon_id,
    activity_season_history_exhausted,
    created_at,
    updated_at
)
SELECT
    gen_random_uuid(),
    riot_puuid,
    riot_game_name,
    riot_tag_line,
    profile_icon_id,
    activity_season_history_exhausted,
    created_at,
    updated_at
FROM user_riot_account;

ALTER TABLE user_riot_account ADD COLUMN riot_account_id UUID;

UPDATE user_riot_account ura
SET riot_account_id = ra.id
FROM riot_account ra
WHERE ra.riot_puuid = ura.riot_puuid;

ALTER TABLE user_riot_account ALTER COLUMN riot_account_id SET NOT NULL;

ALTER TABLE user_riot_account
    ADD CONSTRAINT fk_user_riot_account_riot_account
        FOREIGN KEY (riot_account_id) REFERENCES riot_account (id);

ALTER TABLE user_riot_account
    ADD CONSTRAINT uq_user_riot_account_riot_account_id UNIQUE (riot_account_id);

ALTER TABLE user_riot_account DROP CONSTRAINT IF EXISTS uq_user_riot_account_puuid;
ALTER TABLE user_riot_account DROP COLUMN riot_puuid;
ALTER TABLE user_riot_account DROP COLUMN riot_game_name;
ALTER TABLE user_riot_account DROP COLUMN riot_tag_line;
ALTER TABLE user_riot_account DROP COLUMN profile_icon_id;
ALTER TABLE user_riot_account DROP COLUMN activity_season_history_exhausted;

ALTER TABLE riot_account ENABLE ROW LEVEL SECURITY;
