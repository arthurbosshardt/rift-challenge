ALTER TABLE user_riot_account
    ADD COLUMN profile_icon_id INT;

ALTER TABLE user_riot_account
    ADD CONSTRAINT uq_user_riot_account_user_id UNIQUE (user_id);
