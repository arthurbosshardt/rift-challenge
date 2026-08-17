CREATE TABLE race_duo (
    id          UUID PRIMARY KEY,
    race_id     UUID NOT NULL REFERENCES race (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_race_duo_race_id ON race_duo (race_id);

ALTER TABLE race_participant
    ADD COLUMN duo_id UUID REFERENCES race_duo (id) ON DELETE CASCADE;

CREATE INDEX idx_race_participant_duo_id ON race_participant (duo_id);
