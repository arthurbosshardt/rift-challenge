ALTER TABLE user_riot_account
    DROP CONSTRAINT uq_user_riot_account_user_id;

ALTER TABLE user_riot_account
    ADD COLUMN is_primary BOOLEAN NOT NULL DEFAULT false;

UPDATE user_riot_account
SET is_primary = true;

CREATE UNIQUE INDEX uq_user_riot_account_one_primary_per_user
    ON user_riot_account (user_id)
    WHERE is_primary = true;
