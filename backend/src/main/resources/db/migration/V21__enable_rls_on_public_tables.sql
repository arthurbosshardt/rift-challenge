-- The backend connects as the table-owning Postgres role (rolbypassrls = true), so it is
-- unaffected by RLS. anon/authenticated (used by the Supabase JS client for auth) get no
-- policies here, so PostgREST denies them by default. Matches the existing app_user setup.
-- flyway_schema_history is excluded: it holds no user data and Flyway itself needs
-- unrestricted access to it.
ALTER TABLE challenge ENABLE ROW LEVEL SECURITY;
ALTER TABLE challenge_participant ENABLE ROW LEVEL SECURITY;
ALTER TABLE challenge_refresh ENABLE ROW LEVEL SECURITY;
ALTER TABLE riot_match ENABLE ROW LEVEL SECURITY;
ALTER TABLE rank_snapshot ENABLE ROW LEVEL SECURITY;
ALTER TABLE challenge_participant_match ENABLE ROW LEVEL SECURITY;
ALTER TABLE challenge_duo ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_riot_account ENABLE ROW LEVEL SECURITY;
