-- Singleton cache row for the global leaderboard (recomputed 4x/day by a scheduled job,
-- never on user demand). Mirrors challenge_list_cache_state.
CREATE TABLE leaderboard_cache (
    id              SMALLINT PRIMARY KEY DEFAULT 1,
    payload         TEXT NOT NULL,
    generated_at    TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_leaderboard_cache_singleton CHECK (id = 1)
);

-- Consistent with V21/V22: the backend bypasses RLS via its table-owning role.
ALTER TABLE leaderboard_cache ENABLE ROW LEVEL SECURITY;
