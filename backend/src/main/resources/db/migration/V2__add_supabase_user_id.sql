ALTER TABLE app_user
    ADD COLUMN supabase_id UUID;

CREATE UNIQUE INDEX uq_app_user_supabase_id ON app_user (supabase_id)
    WHERE supabase_id IS NOT NULL;
