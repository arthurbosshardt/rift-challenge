-- Update share_slug to use the challenge id (UUID) instead of the name
-- This ensures sharing links remain stable even when the challenge name changes
UPDATE challenge SET share_slug = CAST(id AS VARCHAR(36));
