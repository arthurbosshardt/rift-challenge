-- Tracks the season match total (wins + losses) that was current when
-- activity_season_history_exhausted was last set to true. Without this, a stale
-- exhausted flag permanently blocks background sync once a linked account plays a
-- new ranked game and its live season total grows past the stored match count.
ALTER TABLE riot_account
    ADD COLUMN activity_season_history_exhausted_total INT;
