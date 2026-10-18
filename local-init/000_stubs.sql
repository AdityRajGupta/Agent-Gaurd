-- Local-dev stub (runs BEFORE the Supabase migrations).
-- 002_rls_policies.sql references Supabase's auth.jwt(), which does not exist
-- on vanilla postgres. This stub makes 002 apply cleanly for local docker dev.
-- Supabase cloud ignores this file (it has a real auth schema).
create schema if not exists auth;

create or replace function auth.jwt()
returns jsonb language sql stable as $$
  select '{}'::jsonb
$$;
