CREATE TABLE riot_match (
    id              UUID PRIMARY KEY,
    riot_match_id   VARCHAR(32) NOT NULL,
    queue_id        INT NOT NULL,
    game_start      TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_riot_match_id UNIQUE (riot_match_id)
);

CREATE INDEX idx_riot_match_game_start ON riot_match (game_start);

CREATE TABLE rank_snapshot (
    id              UUID PRIMARY KEY,
    participant_id  UUID NOT NULL REFERENCES race_participant (id) ON DELETE CASCADE,
    captured_at     TIMESTAMPTZ NOT NULL,
    snapshot_type   VARCHAR(16) NOT NULL,
    queue_type      VARCHAR(32) NOT NULL,
    tier            VARCHAR(16) NOT NULL,
    rank_division   VARCHAR(4),
    league_points   INT NOT NULL,
    wins            INT,
    losses          INT,
    CONSTRAINT chk_snapshot_type CHECK (snapshot_type IN ('BASELINE', 'REFRESH'))
);

CREATE INDEX idx_rank_snapshot_participant ON rank_snapshot (participant_id, captured_at DESC);

CREATE TABLE race_participant_match (
    id              UUID PRIMARY KEY,
    race_id         UUID NOT NULL REFERENCES race (id) ON DELETE CASCADE,
    participant_id  UUID NOT NULL REFERENCES race_participant (id) ON DELETE CASCADE,
    riot_match_id   VARCHAR(32) NOT NULL REFERENCES riot_match (riot_match_id),
    win             BOOLEAN NOT NULL,
    CONSTRAINT uq_participant_match UNIQUE (participant_id, riot_match_id)
);

CREATE INDEX idx_rpm_race_id ON race_participant_match (race_id);
