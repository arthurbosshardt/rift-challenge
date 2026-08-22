-- Older imports stored Riot gameDuration as milliseconds; convert to seconds.

UPDATE leaderboard_account_match
SET game_duration_seconds = game_duration_seconds / 1000
WHERE game_duration_seconds > 10000;
