# 🛡️ AgentGuard

Governance platform for autonomous AI agents: MCP-native tool runtime, deterministic
policy enforcement, ML anomaly detection on agent behavior, RAG-grounded
investigations, and human-in-the-loop approvals. The LLM proposes — deterministic
code validates, humans approve.

Full spec: [`PRD.md`](PRD.md) · Build plan: [`docs/PHASES.md`](docs/PHASES.md) ·
Changes: [`CHANGES.md`](CHANGES.md)
Plain-language run, deploy, and interview guide: [`docs/PROJECT_GUIDE.md`](docs/PROJECT_GUIDE.md)
Printable guide: [`output/pdf/agentguard_project_guide.pdf`](output/pdf/agentguard_project_guide.pdf)

## Architecture

```
                    ┌─────────────────────────┐
                    │   Next.js dashboard     │  :3000  (Vercel)
                    │  runs · cases · policy  │
                    └────────────┬────────────┘
                                 │ JWT
                    ┌────────────▼────────────┐
                    │  Spring Boot backend    │  :8080  (Render/Fly)
                    │  policy proxy · RBAC    │
                    │  anomaly scoring · RAG   │
                    └─┬──────────┬──────────┬─┘
                      │          │          │
        ┌─────────────▼─┐  ┌─────▼──────┐  ┌▼──────────────────┐
        │ MCP tools     │  │ ML service │  │ LLM (free tier)   │
        │ :8002         │  │ :8001      │  │ Ollama local      │
        │ 5 sandboxed   │  │ Isolation  │  │ Groq/Gemini free  │
        │ tools         │  │ Forest     │  │ auto-fallback     │
        └───────────────┘  └────────────┘  └───────────────────┘
                      ┌────────────▼────────────┐
                      │  Supabase / PostgreSQL  │
                      │  RLS · pgvector · audit │
                      └─────────────────────────┘
```

Every agent tool call flows: `agent --API key--> proxy --policy--> anomaly score --> MCP tool`.
Denied calls cite the exact policy rule. Over-threshold scores open cases with
RAG investigations. High-sensitivity tools fail closed to human approval.
`code_exec` has process resource limits, but it does not yet have network
isolation; do not use it for untrusted code in a production deployment.

## Quickstart (local)

1. If `.env` does not already exist, run `cp .env.example .env`. Set a long
   random `JWT_SECRET`. Keep `.env` private; it is gitignored. Local Compose
   uses its own Postgres container and default internal MCP token.
2. `docker compose up --build -d` — the `db` container auto-creates the schema
   on first start (see `docker-compose.override.yml`).
3. `./demo/all_tests.sh` — runs the Phase 2/3/4 demos end to end.
   Phase 4 needs an LLM: install Ollama, run `ollama serve`, then
   `ollama pull llama3.1:8b` and `ollama pull nomic-embed-text`; or set
   `GROQ_API_KEY` / `GEMINI_API_KEY` for a free tier.
4. Dashboard (optional): `docker compose --profile ui up --build -d`, open
   http://localhost:3000, log in as your admin user.
5. API docs: http://localhost:8080/swagger-ui.html

## Deploy

| Part | Where | How |
|---|---|---|
| Dashboard | Vercel | Import repo, root directory `frontend`, set `NEXT_PUBLIC_API_URL` to your backend URL |
| Backend + ML + MCP | Render | `render.yaml` blueprint in repo root; configure database credentials, service URLs, matching `MCP_SERVER_TOKEN` values, `CORS_ALLOWED_ORIGINS`, and optional Groq/Gemini keys. Review current free plan limits. |
| Database | Supabase | Apply `supabase/migrations/*.sql` in the SQL editor (in order) |

## Repo layout

| Dir | What |
|---|---|
| `backend/` | Spring Boot 3 (Java 21) — proxy, policy engine, anomaly wiring, RAG, RBAC, audit |
| `supabase/migrations/` | PostgreSQL schema + RLS + pgvector |
| `ml-service/` | FastAPI Isolation Forest scorer, training + eval scripts |
| `mcp-servers/` | 5 MCP-style tools over HTTP; requests require the proxy's internal token |
| `frontend/` | Next.js 14 dashboard (login, runs, cases, policies, audit) |
| `demo/` | Per-phase demo scripts + `all_tests.sh` |
| `docs/` | Phase plan and plain-language project guide |

## Current status

**Phases 1–5 implemented.** Local verification: Phase 2 policy proxy and
Phase 3 anomaly demos pass (red-team eval F1 1.000). Phase 4 investigation is
implemented but needs Ollama or a Groq/Gemini key to run. A public deployment,
hosted URL, production credentials, and recorded walkthrough are still pending.
