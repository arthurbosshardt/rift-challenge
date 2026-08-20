ALTER TABLE challenge
    DROP CONSTRAINT IF EXISTS chk_challenge_end_after_start;

ALTER TABLE challenge
    ALTER COLUMN end_at DROP NOT NULL;

ALTER TABLE challenge
    ADD COLUMN max_games INTEGER;

ALTER TABLE challenge
    ADD CONSTRAINT chk_challenge_end_mode CHECK (
        (end_at IS NOT NULL AND max_games IS NULL AND end_at > start_at)
        OR (end_at IS NULL AND max_games IS NOT NULL AND max_games > 0)
    );
