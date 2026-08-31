-- leaderboard_account_rank only ever holds the latest snapshot (upserted in place on every
-- sync), so the app has no way to know what an account's rank actually was N days ago. LP-gained
-- figures fall back to a heuristic reconstruction from the win/loss sequence, which is blind to
-- the account's real rank and structurally underestimates gains across multiple divisions
-- (promotion bonuses aren't modeled). This adds an insert-only, timestamped history of the same
-- data so future LP-gained figures can be computed as an exact score delta between two real
-- snapshots once enough history has accumulated (see LeaderboardComputationService).

CREATE TABLE leaderboard_account_rank_history (
    id              UUID PRIMARY KEY,
    riot_puuid      VARCHAR(78) NOT NULL,
    captured_at     TIMESTAMPTZ NOT NULL,
    tier            VARCHAR(16) NOT NULL,
    rank_division   VARCHAR(4),
    league_points   INT NOT NULL
);

CREATE INDEX idx_leaderboard_account_rank_history_puuid_captured_at
    ON leaderboard_account_rank_history (riot_puuid, captured_at DESC);

ALTER TABLE leaderboard_account_rank_history ENABLE ROW LEVEL SECURITY;
