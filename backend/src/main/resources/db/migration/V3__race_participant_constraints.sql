ALTER TABLE race_participant
    ALTER COLUMN riot_puuid SET NOT NULL,
    ALTER COLUMN riot_game_name SET NOT NULL,
    ALTER COLUMN riot_tag_line SET NOT NULL;

ALTER TABLE race_participant
    ADD CONSTRAINT uq_race_participant_race_puuid UNIQUE (race_id, riot_puuid);
