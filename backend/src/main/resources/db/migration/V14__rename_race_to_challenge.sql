-- Rename race tables/columns to challenge. Historical V1-V13 migrations stay unchanged.

ALTER TABLE race RENAME TO challenge;
ALTER TABLE race_participant RENAME TO challenge_participant;
ALTER TABLE race_refresh RENAME TO challenge_refresh;
ALTER TABLE race_participant_match RENAME TO challenge_participant_match;
ALTER TABLE race_duo RENAME TO challenge_duo;

ALTER TABLE challenge_participant RENAME COLUMN race_id TO challenge_id;
ALTER TABLE challenge_refresh RENAME COLUMN race_id TO challenge_id;
ALTER TABLE challenge_participant_match RENAME COLUMN race_id TO challenge_id;
ALTER TABLE challenge_duo RENAME COLUMN race_id TO challenge_id;

DO $$
DECLARE
    rec RECORD;
    new_name TEXT;
BEGIN
    FOR rec IN
        SELECT con.conname, rel.relname
        FROM pg_constraint con
        JOIN pg_class rel ON rel.oid = con.conrelid
        JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
        WHERE nsp.nspname = 'public'
          AND con.conname LIKE '%race%'
    LOOP
        new_name := replace(rec.conname, 'race', 'challenge');
        IF new_name <> rec.conname THEN
            EXECUTE format('ALTER TABLE %I RENAME CONSTRAINT %I TO %I', rec.relname, rec.conname, new_name);
        END IF;
    END LOOP;

    FOR rec IN
        SELECT indexname AS conname
        FROM pg_indexes
        WHERE schemaname = 'public'
          AND indexname LIKE '%race%'
    LOOP
        new_name := replace(rec.conname, 'race', 'challenge');
        IF new_name <> rec.conname THEN
            EXECUTE format('ALTER INDEX %I RENAME TO %I', rec.conname, new_name);
        END IF;
    END LOOP;
END $$;
