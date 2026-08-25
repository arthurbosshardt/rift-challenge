-- Speeds up the cross-player champion-rank query, which groups by champion_id across all accounts.

CREATE INDEX idx_leaderboard_account_match_champion_id ON leaderboard_account_match (champion_id);
