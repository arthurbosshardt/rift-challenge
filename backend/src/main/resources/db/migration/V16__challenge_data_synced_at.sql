ALTER TABLE challenge
    ADD COLUMN data_synced_at TIMESTAMPTZ;

UPDATE challenge c
SET data_synced_at = cr.refreshed_at
FROM challenge_refresh cr
WHERE cr.challenge_id = c.id;

UPDATE challenge c
SET data_synced_at = snapshot_times.latest_snapshot_at
FROM (
    SELECT cp.challenge_id, MAX(rs.captured_at) AS latest_snapshot_at
    FROM challenge_participant cp
    JOIN rank_snapshot rs ON rs.participant_id = cp.id
    GROUP BY cp.challenge_id
) snapshot_times
WHERE snapshot_times.challenge_id = c.id
  AND c.data_synced_at IS NULL;
