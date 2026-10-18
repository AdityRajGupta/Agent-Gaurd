-- AgentGuard Row-Level Security — apply AFTER 001
-- Assumes Supabase Auth (auth.uid()) for human users.
-- Service-role key bypasses RLS (used by the Spring Boot backend).

-- helper: current user's role from our users table via auth email match
-- (simplest wiring: users.username = auth email)
create or replace function public.current_role()
returns text language sql stable as $$
  select role from public.users where username = auth.jwt() ->> 'email' limit 1
$$;

alter table users               enable row level security;
alter table agent_identities    enable row level security;
alter table agent_api_keys      enable row level security;
alter table agent_runs          enable row level security;
alter table tool_definitions    enable row level security;
alter table policies            enable row level security;
alter table tool_events         enable row level security;
alter table access_edges        enable row level security;
alter table anomaly_cases       enable row level security;
alter table decisions           enable row level security;
alter table audit_log           enable row level security;
alter table embeddings          enable row level security;

-- AUDIT: append-only. No UPDATE/DELETE for anyone via RLS (only INSERT+SELECT).
create policy "audit select" on audit_log for select
  using (true);
create policy "audit insert" on audit_log for insert
  with check (true);
-- (intentionally no update/delete policies → denied)

-- users: admins manage, everyone reads self
create policy "users self read" on users for select
  using (username = auth.jwt() ->> 'email' or public.current_role() = 'ADMIN');
create policy "users admin write" on users for all
  using (public.current_role() = 'ADMIN');

-- agents & policies: operators read, admins write
create policy "agents read" on agent_identities for select
  using (public.current_role() in ('ADMIN','OPERATOR','AUDITOR'));
create policy "agents admin write" on agent_identities for all
  using (public.current_role() = 'ADMIN');

create policy "policies read" on policies for select
  using (public.current_role() in ('ADMIN','OPERATOR','AUDITOR'));
create policy "policies admin write" on policies for all
  using (public.current_role() = 'ADMIN');

create policy "tools read" on tool_definitions for select
  using (true);  -- tool catalog is public metadata

-- events/cases: operators+ read, backend (service role) writes
create policy "events read" on tool_events for select
  using (public.current_role() in ('ADMIN','OPERATOR','AUDITOR'));
create policy "events insert" on tool_events for insert
  with check (true);  -- service role inserts; tighten per-agent in Phase 2

create policy "cases read" on anomaly_cases for select
  using (public.current_role() in ('ADMIN','OPERATOR','AUDITOR'));
create policy "cases operator update" on anomaly_cases for update
  using (public.current_role() in ('ADMIN','OPERATOR'));

create policy "decisions write" on decisions for insert
  with check (public.current_role() in ('ADMIN','OPERATOR'));
create policy "decisions read" on decisions for select
  using (public.current_role() in ('ADMIN','OPERATOR','AUDITOR'));

create policy "runs read" on agent_runs for select
  using (public.current_role() in ('ADMIN','OPERATOR','AUDITOR'));

create policy "keys admin" on agent_api_keys for all
  using (public.current_role() = 'ADMIN');

create policy "edges read" on access_edges for select
  using (public.current_role() in ('ADMIN','OPERATOR','AUDITOR'));

create policy "embeddings read" on embeddings for select
  using (public.current_role() in ('ADMIN','OPERATOR'));
