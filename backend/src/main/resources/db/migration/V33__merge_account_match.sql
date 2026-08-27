-- Consolidates the two parallel match-tracking tables into one: leaderboard_account_match already
-- holds full combat stats per tracked account, independent of any challenge; challenge_participant_match
-- held only win/champion per challenge participant, with challenge progress recomputed live against
-- this single table (filtered by each challenge's own date window / max-games cap) instead of via a
-- persisted per-challenge junction table.

ALTER TABLE leaderboard_account_match RENAME TO account_match;
ALTER INDEX uq_leaderboard_account_match RENAME TO uq_account_match;
ALTER INDEX idx_leaderboard_account_match_puuid RENAME TO idx_account_match_puuid;
ALTER INDEX idx_leaderboard_account_match_champion_id RENAME TO idx_account_match_champion_id;

-- Rows migrated from challenge_participant_match never had combat stats tracked, so challenges
-- that read past matches need a way to tell those rows apart from freshly-synced ones.
ALTER TABLE account_match ADD COLUMN historical BOOLEAN NOT NULL DEFAULT FALSE;

INSERT INTO account_match (id, riot_puuid, riot_match_id, win, champion_id, historical)
SELECT gen_random_uuid(), cp.riot_puuid, cpm.riot_match_id, cpm.win, cpm.champion_id, TRUE
FROM challenge_participant_match cpm
JOIN challenge_participant cp ON cp.id = cpm.participant_id
ON CONFLICT (riot_puuid, riot_match_id) DO NOTHING;

DROP TABLE challenge_participant_match;
