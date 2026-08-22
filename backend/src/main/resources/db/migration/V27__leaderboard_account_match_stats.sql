-- Per-match stats for account activity (Mes statistiques) without live Riot detail fan-out.

ALTER TABLE leaderboard_account_match
    ADD COLUMN champion_name         VARCHAR(32),
    ADD COLUMN kills                 INT,
    ADD COLUMN deaths                INT,
    ADD COLUMN assists               INT,
    ADD COLUMN cs                    INT,
    ADD COLUMN game_duration_seconds BIGINT;
