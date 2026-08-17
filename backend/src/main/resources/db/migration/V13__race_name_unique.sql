-- Resolve case-insensitive duplicate names before enforcing uniqueness.
WITH ranked AS (
    SELECT
        id,
        name,
        ROW_NUMBER() OVER (
            PARTITION BY LOWER(BTRIM(name))
            ORDER BY created_at, id
        ) AS rn
    FROM race
)
UPDATE race AS r
SET name = LEFT(BTRIM(r.name), 115) || ' (' || ranked.rn || ')'
FROM ranked
WHERE r.id = ranked.id
  AND ranked.rn > 1;

CREATE UNIQUE INDEX uq_race_name_normalized ON race (LOWER(BTRIM(name)));
