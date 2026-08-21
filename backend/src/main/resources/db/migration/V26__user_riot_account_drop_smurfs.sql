DELETE FROM user_riot_account
WHERE is_primary = false;

DROP INDEX uq_user_riot_account_one_primary_per_user;

ALTER TABLE user_riot_account
    DROP COLUMN is_primary;

ALTER TABLE user_riot_account
    ADD CONSTRAINT uq_user_riot_account_user_id UNIQUE (user_id);
