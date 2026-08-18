ALTER TABLE challenge DROP CONSTRAINT IF EXISTS uq_race_share_slug;
ALTER TABLE challenge DROP CONSTRAINT IF EXISTS uq_challenge_share_slug;

ALTER TABLE challenge
    ALTER COLUMN share_slug TYPE VARCHAR(120)
    USING name;

ALTER TABLE challenge
    ADD CONSTRAINT uq_challenge_share_slug UNIQUE (share_slug);
