-- Lazily detected once per account (probe-and-cache on first leaderboard sync); null until then.
ALTER TABLE riot_account ADD COLUMN region VARCHAR(8);
