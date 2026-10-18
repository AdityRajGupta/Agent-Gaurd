# AgentGuard — Product Requirements Document

**Version:** 1.0 · **Date:** 2026-10-18 · **Owner:** Aditya Raj
**Status:** Phase 1 scaffold

---

## 1. Vision

AgentGuard is a governance platform for autonomous AI agents. Teams deploy agents that use tools (via MCP servers) to do real work — AgentGuard makes sure those agents stay inside their declared task: it registers agent identities, enforces tool-use policies, scores every action with an ML anomaly model, investigates violations with a RAG agent, and requires human approval for consequential actions. Nothing executes on an agent's word alone.

**One-line demo:** *"Watch this agent get prompt-injected live — and watch AgentGuard catch it, explain the violation with evidence, and block the tool call."*

## 2. Objectives

1. Provide a working MCP-native agent runtime with tool governance (not a chatbot wrapper).
2. Detect anomalous agent behavior with a trained, evaluable ML model (Isolation Forest) — report precision/recall from red-team scenarios.
3. Ground every LLM explanation in retrieved evidence (RAG over pgvector); the LLM proposes, deterministic code + humans dispose.
4. Ship a real backend: Spring Boot 3, JWT + RBAC, Supabase/PostgreSQL with Row-Level Security, full audit trail.
5. Produce a portfolio-grade repo: Docker Compose one-command run, CI, README with architecture diagram, live demo.

## 3. Users & Roles

| Role | Capabilities |
|---|---|
| `ADMIN` | Manage agents, tools, policies; approve/deny high-risk actions; view everything |
| `OPERATOR` | Run agents, review anomaly cases, approve/deny assigned actions |
| `AUDITOR` (read-only) | View audit log, cases, policies; cannot approve or execute |
| `AGENT` (machine identity) | Authenticated via API key; can only call tools its policy allows |

## 4. Functional Requirements

### 4.1 Agent Registry (Phase 1)
- CRUD for agent identities: name, owner, declared task description, allowed tool allowlist, risk tier.
- API-key issuance/rotation per agent; keys are hashed at rest (BCrypt/SHA-256 + salt).
- Agent run lifecycle: `CREATED → RUNNING → AWAITING_APPROVAL → COMPLETED | BLOCKED | FAILED`.

### 4.2 MCP Tool Servers (Phase 2)
- Python MCP servers exposing typed tools. MVP tools (5):
  - `web_search(query)` — read-only web lookup (mockable)
  - `db_query(sql)` — parameterized, read-only, allowlisted tables only
  - `file_read(path)` / `file_write(path)` — sandboxed to a workspace dir
  - `code_exec(code)` — sandboxed execution with CPU/time/memory limits, no network
- Every tool call goes through the **policy enforcement proxy** (Spring Boot) — tools are never called directly by the agent.

### 4.3 Policy Engine (Phase 1–2)
- Policies are data, not code: `{ agent_id, tool, constraints (max_calls, data_sensitivity, require_approval), effect: ALLOW | DENY | APPROVE }`.
- Deterministic evaluation on every tool call (<10 ms p99 target locally).
- Denied calls return a structured denial citing the exact policy rule.

### 4.4 Event Ingestion & Anomaly Detection (Phase 3)
- Every tool call emits a `tool_events` row: agent, tool, args hash, timestamp, data sensitivity, sequence position.
- Feature pipeline (per agent, sliding window): tool-call frequency, rare-tool usage, sensitive-data access count, sequence n-gram novelty vs. baseline, time-of-day deviation.
- Isolation Forest model (scikit-learn, trained per agent on benign runs) served via FastAPI; Spring Boot calls it synchronously with a timeout + fallback (fail-closed: on scorer outage, high-sensitivity tools require approval).
- Score → `anomaly_cases` when above threshold; thresholds tunable per risk tier.

### 4.5 Investigation Agent — RAG (Phase 4)
- On case creation, a RAG pipeline retrieves: the agent's declared task, the violated policy text, the tool-call sequence, and similar past cases (pgvector similarity).
- LLM drafts: violation summary, evidence timeline (each claim cites an event id), recommended containment (`REVOKE_TOOL`, `PAUSE_AGENT`, `REQUIRE_APPROVAL`).
- The LLM **cannot** execute containment — it only proposes; a human approves.

### 4.6 Approvals & Human-in-the-Loop (Phase 2)
- Actions requiring approval create `decisions` rows; approvers see the evidence bundle.
- `APPROVE` / `DENY` with reason; every decision is audit-logged with actor + timestamp.

### 4.7 Audit Trail (Phase 1)
- Append-only `audit_log`: actor, action, target, before/after hash, timestamp. No UPDATE/DELETE grants on this table (RLS + revoked privileges).

### 4.8 Dashboard (Phase 5)
- Next.js: live agent run view (SSE stream of tool calls + scores), case queue, policy editor, audit explorer, red-team scenario runner.

## 5. Non-Functional Requirements

- **Security:** JWT auth, RBAC, RLS on all tenant tables, secrets in env (never in repo), sandboxed code exec, parameterized DB access, rate limits on ingestion.
- **Reliability:** policy proxy fail-closed on ML outage; idempotent event ingestion (dedupe on `event_id`).
- **Performance targets (local):** policy check p99 < 10 ms; anomaly score p99 < 300 ms; dashboard SSE latency < 1 s.
- **Portability:** `docker compose up` runs backend + ml-service + postgres(pgvector) + frontend.

## 6. AI/ML Design

| Component | Approach | Evaluation |
|---|---|---|
| Anomaly scorer | Isolation Forest per agent on benign tool-call features | Red-team scenarios (prompt injection, tool misuse, exfiltration): precision/recall/F1 |
| Investigation | RAG (pgvector) + LLM, citations required | Manual review: % claims with valid event citations |
| Tool args | Schema validation (deterministic), never LLM-parsed | Unit tests on adversarial inputs |

**Ground rule (repo-wide):** the LLM proposes, deterministic code validates, a human approves. Document any exception.

## 7. API Surface (v1)

| Method | Path | Role |
|---|---|---|
| POST | `/api/v1/agents` | ADMIN |
| GET | `/api/v1/agents/{id}` | OPERATOR+ |
| POST | `/api/v1/agents/{id}/api-keys` | ADMIN |
| POST | `/api/v1/tools/{tool}/invoke` | AGENT (via proxy) |
| GET/POST | `/api/v1/policies` | ADMIN |
| GET | `/api/v1/cases` | OPERATOR+ |
| GET | `/api/v1/cases/{id}` | OPERATOR+ |
| POST | `/api/v1/decisions` | OPERATOR+ |
| GET | `/api/v1/audit` | AUDITOR+ |
| GET | `/api/v1/runs/{id}/stream` (SSE) | OPERATOR+ |
| POST | `/api/v1/ml/score` (internal, ml-service) | service |

Full OpenAPI will be served at `/swagger-ui.html` (springdoc).

## 8. Data Model

Core tables (see `supabase/migrations/001_core_schema.sql`):
`agent_identities`, `agent_api_keys`, `tool_definitions`, `policies`,
`tool_events`, `access_edges`, `anomaly_cases`, `decisions`, `audit_log`,
`agent_runs`, `embeddings` (pgvector).

## 9. Phase Plan

| Phase | Scope | Exit criteria |
|---|---|---|
| **1 — Foundation** ✅ (this scaffold) | Spring Boot skeleton (auth, RBAC, CRUD, audit), Supabase schema + RLS, docker-compose, CI | `mvn compile` green; migrations apply cleanly |
| **2 — Agent runtime** | MCP tool servers (5 tools), policy proxy, approvals, run lifecycle, SSE stream | Demo agent completes a benign task; denied call returns cited denial |
| **3 — Anomaly detection** | Feature pipeline, train Isolation Forest per agent, FastAPI scorer, cases | Red-team eval: F1 reported on 3 attack scenarios |
| **4 — RAG investigation** | pgvector retrieval, evidence-cited explanations, containment proposals | ≥90% of claims cite valid events (manual sample) |
| **5 — Hardening & demo** | Next.js dashboard, red-team runner UI, README diagram, live deploy | One-command demo; 3-min recorded walkthrough |

Detailed per-phase task lists: `docs/PHASES.md`.

## 10. Risks & Mitigations

| Risk | Mitigation |
|---|---|
| LLM API cost/latency | Cache investigations per case; cap tokens; local fallback templates |
| Sandbox escape in `code_exec` | No network, strict rlimits, separate container; never run untrusted code on host |
| ML false positives erode trust | Per-tier thresholds; human review queue; report precision/recall honestly |
| Scope creep | Phase exit criteria are the gate — no Phase 3 work until Phase 2 demo passes |

## 11. Resume Bullets (use after Phase 4+)

- *Built AgentGuard, an MCP-native AI agent governance platform (Java/Spring Boot, Python, Supabase/PostgreSQL): policy-enforcement proxy authorizing every tool call with <10 ms p99, RBAC across 4 roles, and append-only audit log secured by Row-Level Security.*
- *Trained per-agent Isolation Forest anomaly models on tool-call behavior; evaluated against red-team prompt-injection scenarios, reporting precision/recall — violations trigger RAG-grounded investigations with evidence-cited timelines over pgvector.*
- *Shipped 5 sandboxed MCP tool servers and a human-in-the-loop approval workflow; LLM proposes containment actions, deterministic code validates, humans approve — no autonomous execution.*

---

*End of PRD v1.0*
