-- Keep share links stable when a challenge is renamed: slug = challenge id.
UPDATE challenge
SET share_slug = CAST(id AS VARCHAR(36))
WHERE share_slug IS DISTINCT FROM CAST(id AS VARCHAR(36));
