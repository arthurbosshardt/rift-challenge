-- BDD cache of the challenge list (replaces per-request recomputation) and removal of the
-- public/private notion: every challenge is now visible to everyone.

CREATE TABLE challenge_list_cache_state (
    id              SMALLINT PRIMARY KEY DEFAULT 1,
    generated_at    TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_challenge_list_cache_state_singleton CHECK (id = 1)
);

CREATE TABLE challenge_summary_cache (
    challenge_id    UUID PRIMARY KEY REFERENCES challenge (id) ON DELETE CASCADE,
    start_at        TIMESTAMPTZ NOT NULL,
    payload         TEXT NOT NULL,
    generated_at    TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_challenge_summary_cache_start_at ON challenge_summary_cache (start_at DESC);

-- Consistent with V21: the backend bypasses RLS via its table-owning role.
ALTER TABLE challenge_list_cache_state ENABLE ROW LEVEL SECURITY;
ALTER TABLE challenge_summary_cache ENABLE ROW LEVEL SECURITY;

DROP INDEX IF EXISTS idx_challenge_is_public;

ALTER TABLE challenge
    DROP COLUMN is_public;
