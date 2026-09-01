-- Performance indexes surfaced by a code audit.
--
-- challenge_participant had no index on riot_puuid or (riot_game_name, riot_tag_line), despite
-- both being filtered by frequent, public-facing lookups: GET /api/challenges/participating
-- (findDistinctChallengeIdsByRiotPuuidIn) and public summoner/profile resolution
-- (findFirstByRiotGameNameIgnoreCaseAndRiotTagLineIgnoreCaseOrderByCreatedAtDesc).
--
-- riot_account's existing idx_riot_account_game_name indexes the raw column, but the only query
-- that filters on it (findByRiotGameNameIgnoreCaseAndRiotTagLineIgnoreCase) generates a
-- LOWER(...) predicate that index can't satisfy — replaced with a functional index matching the
-- actual predicate.
--
-- account_match's idx_account_match_puuid is redundant: uq_account_match already covers
-- riot_puuid as the leading column of its (riot_puuid, riot_match_id) unique index, so the extra
-- index only cost write throughput on the busiest table in the schema.

CREATE INDEX idx_challenge_participant_riot_puuid ON challenge_participant (riot_puuid);
CREATE INDEX idx_challenge_participant_riot_id_lower ON challenge_participant (LOWER(riot_game_name), LOWER(riot_tag_line));

DROP INDEX idx_riot_account_game_name;
CREATE INDEX idx_riot_account_riot_id_lower ON riot_account (LOWER(riot_game_name), LOWER(riot_tag_line));

DROP INDEX idx_account_match_puuid;
