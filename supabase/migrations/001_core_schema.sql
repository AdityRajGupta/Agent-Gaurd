-- AgentGuard core schema — apply first
-- Run in Supabase SQL editor, in order: 001 then 002.

create extension if not exists "pgcrypto";
create extension if not exists "vector";

-- ---------------------------------------------------------------- users
create table users (
  id          uuid primary key default gen_random_uuid(),
  username    text unique not null,
  password_hash text not null,
  role        text not null check (role in ('ADMIN','OPERATOR','AUDITOR')),
  created_at  timestamptz not null default now()
);

-- ------------------------------------------------------- agent registry
create table agent_identities (
  id            uuid primary key default gen_random_uuid(),
  name          text unique not null,
  owner_id      uuid not null references users(id),
  declared_task text not null,
  risk_tier     text not null default 'standard'
                check (risk_tier in ('low','standard','high')),
  status        text not null default 'ACTIVE'
                check (status in ('ACTIVE','PAUSED','REVOKED')),
  created_at    timestamptz not null default now()
);

create table agent_api_keys (
  id          uuid primary key default gen_random_uuid(),
  agent_id    uuid not null references agent_identities(id) on delete cascade,
  key_hash    text not null,                 -- sha256 hex, never the raw key
  label       text not null default 'default',
  revoked     boolean not null default false,
  created_at  timestamptz not null default now()
);

create table agent_runs (
  id          uuid primary key default gen_random_uuid(),
  agent_id    uuid not null references agent_identities(id),
  status      text not null default 'RUNNING'
              check (status in ('RUNNING','AWAITING_APPROVAL','COMPLETED','BLOCKED','FAILED')),
  started_at  timestamptz not null default now(),
  ended_at    timestamptz
);

-- ------------------------------------------------------- tools & policy
create table tool_definitions (
  id              uuid primary key default gen_random_uuid(),
  name            text unique not null,       -- e.g. 'db_query'
  description     text not null,
  data_sensitivity text not null default 'low'
                  check (data_sensitivity in ('low','medium','high')),
  arg_schema      jsonb not null default '{}'::jsonb,
  created_at      timestamptz not null default now()
);

create table policies (
  id            uuid primary key default gen_random_uuid(),
  agent_id      uuid not null references agent_identities(id) on delete cascade,
  tool_id       uuid not null references tool_definitions(id),
  effect        text not null check (effect in ('ALLOW','DENY','APPROVE')),
  max_calls     integer,                       -- null = unlimited
  created_by    uuid not null references users(id),
  created_at    timestamptz not null default now(),
  unique (agent_id, tool_id)
);

-- ---------------------------------------------------------------- events
create table tool_events (
  id              uuid primary key default gen_random_uuid(),
  event_id        text unique not null,        -- client-supplied idempotency key
  run_id          uuid references agent_runs(id),
  agent_id        uuid not null references agent_identities(id),
  tool_id         uuid not null references tool_definitions(id),
  args_hash       text not null,               -- sha256 of canonical args, not raw args
  policy_decision text not null check (policy_decision in ('ALLOW','DENY','APPROVE')),
  anomaly_score   double precision,            -- filled by ml-service (Phase 3)
  created_at      timestamptz not null default now()
);
create index tool_events_agent_time on tool_events (agent_id, created_at desc);

-- data-access graph edges (Phase 3+)
create table access_edges (
  id          uuid primary key default gen_random_uuid(),
  agent_id    uuid not null references agent_identities(id),
  tool_id     uuid not null references tool_definitions(id),
  resource    text not null,                   -- e.g. table name, url host
  sensitivity text not null default 'low',
  first_seen  timestamptz not null default now(),
  last_seen   timestamptz not null default now(),
  unique (agent_id, tool_id, resource)
);

-- ---------------------------------------------------------------- cases
create table anomaly_cases (
  id            uuid primary key default gen_random_uuid(),
  agent_id      uuid not null references agent_identities(id),
  run_id        uuid references agent_runs(id),
  score         double precision not null,
  status        text not null default 'OPEN'
                check (status in ('OPEN','INVESTIGATING','RESOLVED','DISMISSED')),
  summary       text,                          -- RAG investigation (Phase 4)
  created_at    timestamptz not null default now()
);

create table decisions (
  id          uuid primary key default gen_random_uuid(),
  case_id     uuid references anomaly_cases(id),
  event_id    uuid references tool_events(id),
  decided_by  uuid not null references users(id),
  decision    text not null check (decision in ('APPROVE','DENY')),
  reason      text,
  created_at  timestamptz not null default now(),
  check ((case_id is not null) or (event_id is not null))
);

-- ---------------------------------------------------------------- audit (append-only; see 002)
create table audit_log (
  id          uuid primary key default gen_random_uuid(),
  actor       text not null,                   -- username or 'agent:<name>'
  action      text not null,
  target      text not null,
  detail      jsonb not null default '{}'::jsonb,
  created_at  timestamptz not null default now()
);

-- ---------------------------------------------------------------- RAG embeddings (Phase 4)
-- 768 dims = nomic-embed-text (Ollama, local) / text-embedding-004 (Gemini)
create table embeddings (
  id          uuid primary key default gen_random_uuid(),
  source      text not null,                   -- 'policy' | 'tool_doc' | 'case'
  source_id   uuid not null,
  chunk       text not null,
  embedding   vector(768),
  created_at  timestamptz not null default now()
);
create index embeddings_vec on embeddings
  using ivfflat (embedding vector_cosine_ops) with (lists = 100);

-- seed the 5 MVP tools
insert into tool_definitions (name, description, data_sensitivity, arg_schema) values
  ('web_search', 'Read-only web lookup', 'low', '{"query":"string"}'),
  ('db_query',   'Parameterized read-only SQL on allowlisted tables', 'high', '{"sql":"string"}'),
  ('file_read',  'Read a file inside the workspace sandbox', 'medium', '{"path":"string"}'),
  ('file_write', 'Write a file inside the workspace sandbox', 'medium', '{"path":"string","content":"string"}'),
  ('code_exec',  'Execute code in a sandbox (no network, strict limits)', 'high', '{"code":"string","language":"string"}');
