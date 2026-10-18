# Changelog

## UI polish, run reliability, and GitHub readiness (2026-10-19)

- Redesigned the dashboard with a responsive workspace sidebar, clearer page
  hierarchy, refined dark palette, accessible focus states, and compact tables.
- Added configurable API CORS origins so the local dashboard and a hosted
  Vercel site can call the backend.
- Protected MCP tool invocation with a shared backend-to-tool-service token;
  fixed workspace path boundary checks and applied process CPU, memory, and
  file-descriptor limits to code execution. Network-isolated code execution
  remains necessary before production use.
- Disabled the optional paid OpenAI provider path so the configured choices
  stay within Ollama and Groq/Gemini free tiers.
- Set Compose's default LLM mode to `auto`, so a configured free-tier key is
  selected before Ollama without extra configuration.
- Made Phase 2/3 demos safe to rerun by giving their agents and event IDs unique
  names; fixed the OpenAI-compatible provider compile error in `LlmClient`.
- Replaced credentials in `.env.example` with placeholders, added Git ignore
  rules, CI, and a plain-language project/interview guide in Markdown and PDF.
- Preserved the deterministic citation validator in `InvestigationService`.

**Verified locally:** `docker compose up --build -d` succeeds; `./demo/all_tests.sh`
reports 2 passed, 0 failed, Phase 4 skipped (no LLM available); Phase 3
precision/recall/F1 = 1.000. The Next.js production build succeeds in Docker.

## Phases 4+5 — RAG investigation + dashboard + deploy (2026-10-19)

**Phase 4 — RAG investigation (the "perfect" part).**
- `rag/LlmClient`: free provider chain — `auto` (default) prefers GROQ/GEMINI
  free-tier keys when set, else local Ollama; explicit provider choice fails
  loudly on misconfiguration. The LLM only drafts text.
- `rag/EmbeddingClient`: 768-dim embeddings via Ollama (`nomic-embed-text`)
  or Gemini free tier; retrieval degrades gracefully when unavailable.
- `rag/InvestigationService`: gathers agent task + policy rows + tool-call
  timeline + similar past cases (pgvector cosine); LLM drafts summary,
  evidence timeline, and containment proposal; a DETERMINISTIC citation
  validator replaces any `[evt:<id>]` not matching a real recorded event
  with `[unverified]`. Summary saved to the case; status → INVESTIGATING.
- `rag/Embedding`: pgvector-backed entity (no new JDBC dep — String +
  `?::vector` cast, same pattern as the jsonb fix).
- Migration `001`: `embeddings.embedding` is `vector(768)` (was 1536).
- Demo: `demo/phase4_demo.sh` (needs Ollama or a free-tier key; skips cleanly
  otherwise).

**Phase 5 — hardening & demo.**
- `frontend/`: Next.js 14 dashboard (login, overview, agents, cases +
  investigate/resolve/approve, policies, audit). `docker compose --profile ui up`.
- `render.yaml`: Render free-tier blueprint (backend + ml + mcp); DB stays on
  Supabase; frontend goes to Vercel (`NEXT_PUBLIC_API_URL`).
- `.github/workflows/ci.yml`: backend compile, Python syntax, frontend build.
- `GET /api/v1/health` (public) for Render health checks.
- `demo/all_tests.sh`: runs the Phase 2/3/4 demos back to back with a summary.
- README: architecture diagram + deploy table.

## Phase 3 fix — scoring calibration (2026-10-19)

Two bugs found from the first Phase 3 demo run (all 28 calls opened cases,
eval showed 300/300 benign flagged):
1. **Broken score mapping** (`ml-service/app.py`): `clip(0.5 - raw, 0, 1) * 2`
   produced a [0,2] range and mapped mildly-positive decision values to high
   anomaly scores. Replaced with a logistic map `1 / (1 + exp(10·raw))` —
   true [0,1], inliers → near 0, outliers → near 1.
2. **Novelty=1.0 for new agents** (`FeatureService.java`): with no baseline
   history every bigram looked "novel". Now returns 0 when baseline is empty.
3. `demo/phase3_demo.sh`: added idempotent admin register (survives `down -v`)
   and a wait-for-backend loop (no more boot race).
4. **Recalibrated the model (2026-10-19):** the first demo showed attacks
   plateauing at 0.691, just under the 0.7 threshold, while benign scored
   0.57–0.66 — the synthetic training data didn't match real behavior.
   `generate_baseline.py` now includes the quiet corner fresh agents live in
   (30% of benign rows) and realistic burst attacks; the logistic steepness
   went 10 → 20. Verified locally: live benign → 0.156, live attack → 0.761,
   eval precision/recall/F1 = 1.000.

## Phase 3 — Anomaly detection (2026-10-19)

**The ML core is live.** Every ALLOWED tool call is now scored by a per-agent
Isolation Forest; scores above the risk-tier threshold open an `anomaly_cases`
row, and high-sensitivity tools flip to APPROVE (fail-closed) — including when
the scorer itself is unreachable.

**ml-service**
- `app.py`: loads `./models/<agent-id>.joblib` at startup, real scoring,
  `POST /reload` picks up newly trained models.
- `train.py`: per-agent Isolation Forest training (existing, kept).
- `generate_baseline.py` (new): synthetic benign/attack feature CSVs for the
  demo — the scoring pipeline is fully live; only the training baseline is
  synthetic, which is documented, not hidden.
- `eval.py` (new): precision/recall/F1 on labeled benign + attack sets.

**Backend**
- `ml/FeatureService`: 10-min sliding-window features —
  `[call_freq, rare_tool_rate, sensitive_access_count, ngram_novelty, tod_deviation]`.
- `ml/AnomalyScorer`: `/score` client with 3s timeout; empty on failure.
- `cases/`: `AnomalyCase` entity + `GET /api/v1/cases`, `GET /{id}`,
  `PATCH /{id}` (AUDITORs read, OPERATORs triage).
- `ToolEventService`: scores on ALLOW, opens cases, fail-closed flips.
- Thresholds per risk tier in `application.yml` (high 0.5 / standard 0.7 / low 0.85).

**Demo:** `demo/phase3_demo.sh` — train → benign run (low scores) → 25-call
red-team burst (cases open) → eval metrics.

## Phase 2 fixes — local debug session (2026-10-19)

Three bugs found and fixed while getting the Phase 2 demo green:
1. **Empty local DB** (`Schema-validation: missing table [agent_api_keys]`):
   `docker-compose.override.yml` mounts `local-init/000_stubs.sql` (stubs
   Supabase-only `auth.jwt()`) + both Supabase migrations into the db
   entrypoint, so a fresh `docker compose up` builds the schema automatically.
2. **`audit_log.detail` jsonb write failure** (`column "detail" is of type jsonb
   but expression is of type character varying`): added
   `@ColumnTransformer(write = "?::jsonb")` on `AuditLog.detail`.
3. **MCP unreachable from backend** (`toolResult: "mcp unreachable: null"`):
   `MCP_SERVER_URL: http://mcp-servers:8002` added to the backend's
   `environment:` in docker-compose.yml (compose `environment:` overrides
   `.env`, and `localhost` doesn't resolve between containers).

**Demo verified green (2026-10-19):** step 8 ALLOW + real tool result
(`[mock] top results for: MCP protocol`), step 9 DENY citing the policy id,
step 10 APPROVE awaiting a human decision.

## Phase 2 — Agent runtime & policy proxy (2026-10-18)

**New services**
- `mcp-servers/` is now functional: FastAPI on :8002, `POST /invoke` dispatches
  to 5 tools (web_search, db_query with SELECT-only + allowlist, file_read/write
  sandboxed, code_exec with 10s timeout). Added to docker-compose.

**Backend**
- `events/McpClient` — forwards ALLOWED tool calls to the MCP server (JDK HttpClient).
- `events/ToolEventService` — proxy now resolves tool by name, authenticates via
  agent API key, forwards on ALLOW, returns structured denials otherwise.
- `apikeys/` — issue/revoke API keys; raw key shown once, SHA-256 stored.
- `runs/` — run lifecycle (start / status / get) + SSE snapshot stream.
- `decisions/` — human approve/deny endpoint for events and cases.
- `events/ToolDefinition` + `GET /api/v1/tools` catalog.
- `AgentController` / `PolicyController` now derive owner/createdBy from the JWT.

**LLM cost note:** Phase 4 will default to local Ollama — no paid API keys needed.

**Demo:** `demo/phase2_demo.sh` — end-to-end proxy test (ALLOW → tool result,
DENY → cited denial, APPROVE → human decision).

## Phase 1 — Foundation (2026-10-18)

Initial scaffold: Spring Boot auth/RBAC, agent registry, deterministic policy
engine, tool-event recording, append-only audit, Supabase schema + RLS,
FastAPI ML stub, MCP skeleton, docker-compose.
