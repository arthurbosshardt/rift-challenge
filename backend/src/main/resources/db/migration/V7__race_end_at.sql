ALTER TABLE race
    ADD COLUMN end_at TIMESTAMPTZ;

ALTER TABLE race
    ADD CONSTRAINT chk_race_end_after_start CHECK (end_at IS NULL OR end_at > start_at);
