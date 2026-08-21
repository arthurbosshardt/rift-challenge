-- The global leaderboard used to derive its stats from challenge participation, which coupled a
-- player's leaderboard numbers to whichever challenge(s) they joined and how often those got
-- refreshed. This gives the leaderboard its own independent data, synced directly for every
-- linked Riot account regardless of challenge membership (see LeaderboardAccountSyncService).

CREATE TABLE leaderboard_account_match (
    id              UUID PRIMARY KEY,
    riot_puuid      VARCHAR(78) NOT NULL,
    riot_match_id   VARCHAR(32) NOT NULL REFERENCES riot_match (riot_match_id),
    win             BOOLEAN NOT NULL,
    champion_id     INT,
    CONSTRAINT uq_leaderboard_account_match UNIQUE (riot_puuid, riot_match_id)
);

CREATE INDEX idx_leaderboard_account_match_puuid ON leaderboard_account_match (riot_puuid);

CREATE TABLE leaderboard_account_rank (
    riot_puuid      VARCHAR(78) PRIMARY KEY,
    captured_at     TIMESTAMPTZ NOT NULL,
    tier            VARCHAR(16) NOT NULL,
    rank_division   VARCHAR(4),
    league_points   INT NOT NULL
);

ALTER TABLE leaderboard_account_match ENABLE ROW LEVEL SECURITY;
ALTER TABLE leaderboard_account_rank ENABLE ROW LEVEL SECURITY;
