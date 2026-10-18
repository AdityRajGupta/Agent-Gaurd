# AgentGuard — Phase-wise Work Plan

## Phase 1 — Foundation ✅ (this scaffold)
**Goal:** Running skeleton: auth, RBAC, CRUD, audit, DB schema, containers.

- [ ] Create Supabase project → copy URL + anon/service keys into `.env`
- [ ] Apply `supabase/migrations/*.sql` in the Supabase SQL editor (or via CLI)
- [ ] `cd backend && mvn compile` — must be green
- [ ] `docker compose up --build` — backend + postgres(pgvector) healthy
- [ ] Register via `POST /api/v1/auth/register`, login, hit `GET /api/v1/agents` with JWT
- [ ] Create one agent + one policy via API; verify audit_log row appears
**Exit:** compile green, migrations clean, auth + one CRUD round-trip works.

## Phase 2 — Agent Runtime & Policy Proxy ✅ (this zip)
**Goal:** A demo agent that uses tools through the governance proxy.

- [x] Implement 5 MCP tool servers (`mcp-servers/tools_server.py`)
- [x] Build policy enforcement proxy: `POST /api/v1/tools/{tool}/invoke`
  - authenticate agent API key → load policies → ALLOW / DENY / APPROVE
  - denied → structured denial citing rule id
- [x] Approvals: `POST /api/v1/decisions` (approve/deny with reason)
- [x] Run lifecycle + SSE stream `GET /api/v1/runs/{id}/stream`
- [ ] Demo script: benign 3-step task completes end-to-end ← **you run `demo/phase2_demo.sh`**
**Exit:** benign task passes; disallowed tool returns cited denial; approval flow works.

## Phase 3 — Anomaly Detection (the ML core) ✅ (this zip)
**Goal:** Trained per-agent Isolation Forest scoring live tool calls.

- [x] Feature pipeline: sliding-window features per agent (frequency, rare-tool rate,
      sensitive-access count, n-gram novelty, time-of-day deviation)
- [x] Generate benign baselines + train per-agent Isolation Forest (`ml-service/train.py`)
- [x] FastAPI `/score` endpoint; Spring Boot calls with timeout, fail-closed fallback
- [x] Thresholds per risk tier; scores → `anomaly_cases`
- [ ] **Red-team eval:** run `demo/phase3_demo.sh` — report precision/recall/F1 ← **you run this**
**Exit:** F1 reported on red-team set; case queue populates on attacks, stays quiet on benign runs.

## Phase 4 — RAG Investigation ✅ (this zip)
**Goal:** Every case gets an evidence-cited explanation + containment proposal.

- [x] Embed: case summaries → `embeddings` (pgvector, 768-dim, no new JDBC dep)
- [x] On investigate: retrieve task + policy + timeline + similar past cases
- [x] LLM drafts violation summary + timeline (each claim cites `event_id`) + containment proposal
- [x] Citation validator: any `[evt:<id>]` not matching a real recorded event →
      `[unverified]` (deterministic, not LLM-judged)
- [x] Human approves containment from the case view (`/decisions`)
- [ ] Demo script: `./demo/phase4_demo.sh` — needs Ollama or GROQ/GEMINI key ← **you run this**
**Exit:** ≥90% of claims cite valid events (enforced by the validator); no auto-execution anywhere.

## Phase 5 — Hardening & Demo ✅ (this zip)
**Goal:** Portfolio-ready: dashboard, docs, deploy.

- [x] Next.js dashboard: overview, agents, case queue (+ investigate/resolve/approve),
      policy editor, audit explorer (`frontend/`, `docker compose --profile ui up`)
- [x] README architecture diagram + deploy table (docs)
- [x] GitHub Actions CI (backend compile, python syntax, frontend build)
- [x] Deploy configs: `render.yaml` (Render free tier) for backend/ml/mcp;
      frontend → Vercel (`NEXT_PUBLIC_API_URL`); Supabase stays managed
- [ ] Live URL + 3-min demo recording ← **you do this when ready**
**Exit:** `docker compose up` demo + public live URL + resume bullets swapped in.
