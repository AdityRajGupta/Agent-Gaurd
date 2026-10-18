# AgentGuard Dashboard

Next.js 14 dashboard for the AgentGuard governance platform: agent registry,
anomaly case queue with investigation + human approvals, policy editor, and
append-only audit explorer.

## Run locally

```bash
npm install
npm run dev
```

Open http://localhost:3000 and log in with a backend user
(e.g. the admin created by `demo/phase2_demo.sh`).

## Configuration

| Env var | Default | Purpose |
|---|---|---|
| `NEXT_PUBLIC_API_URL` | `http://localhost:8080/api/v1` | AgentGuard backend base URL |

Set it before building when the backend lives elsewhere:

```bash
NEXT_PUBLIC_API_URL=http://backend:8080/api/v1 npm run build
```

## Docker

```bash
docker build -t agentguard-frontend .
docker run -p 3000:3000 -e NEXT_PUBLIC_API_URL=http://localhost:8080/api/v1 agentguard-frontend
```

Note: `NEXT_PUBLIC_*` vars are baked in at build time — pass them as
`--build-arg` (or set them before `npm run build`) for non-default backends.

## Pages

- `/login` — sign in (JWT stored in localStorage)
- `/` — overview: agent count, open cases, recent audit entries
- `/agents` — agent registry + registration form
- `/cases` — open anomaly cases, click through to detail
- `/cases/[id]` — case detail: score, RAG investigation, resolve/dismiss, human approve/deny
- `/policies` — per-agent policy table + policy creation form
- `/audit` — append-only audit log
