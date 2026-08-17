UPDATE race
SET end_at = start_at + INTERVAL '30 days'
WHERE end_at IS NULL;

ALTER TABLE race
    DROP CONSTRAINT IF EXISTS chk_race_end_after_start;

ALTER TABLE race
    ADD CONSTRAINT chk_race_end_after_start CHECK (end_at > start_at);

ALTER TABLE race
    ALTER COLUMN end_at SET NOT NULL;
