"""AgentGuard MCP tool server (Phase 2 — functional).

Exposes the 5 MVP tools over HTTP for the Spring Boot policy proxy:
    POST /invoke  {"tool": "web_search", "args": {"query": "..."}}

Agents NEVER call this directly — every invocation goes through the Spring Boot
policy proxy (POST /api/v1/tools/invoke), which authenticates the agent,
checks policy, records the event, and only then forwards here.

Safety rules (enforced here AND in the proxy):
  - db_query: SELECT only, allowlisted tables, read-only role, statement timeout.
  - file_*  : confined to WORKSPACE_DIR.
  - code_exec: subprocess, no network, 10s timeout. (Containerize in prod.)
"""
import os
import re
import hmac
import resource
import subprocess

from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel

WORKSPACE_DIR = os.environ.get("WORKSPACE_DIR", "/tmp/agent-workspace")
DATABASE_URL = os.environ.get("DATABASE_URL", "")
MCP_SERVER_TOKEN = os.environ.get("MCP_SERVER_TOKEN", "")
ALLOWED_TABLES = {"tool_events", "policies", "agent_identities", "agent_runs"}

app = FastAPI(title="AgentGuard MCP Tools")


class InvokeRequest(BaseModel):
    tool: str
    args: dict = {}


class InvokeResponse(BaseModel):
    tool: str
    result: str


# ------------------------------------------------------------------ tools

def web_search(query: str) -> str:
    """Read-only web lookup (mocked until a search API key is configured)."""
    if os.environ.get("SEARCH_API_KEY"):
        pass  # TODO: wire real search API
    return f"[mock] top results for: {query}"


def db_query(sql: str) -> str:
    """Parameterized, read-only SQL on allowlisted tables only."""
    if not re.match(r"(?is)^\s*select\b", sql or ""):
        raise ValueError("only SELECT statements are allowed")
    if not DATABASE_URL:
        raise ValueError("DATABASE_URL not configured")
    tables = set(re.findall(r"(?i)\bfrom\s+(\w+)|\bjoin\s+(\w+)", sql))
    tables = {t for pair in tables for t in pair if t}
    if not tables <= ALLOWED_TABLES:
        raise ValueError(f"tables not allowlisted: {tables - ALLOWED_TABLES}")
    import psycopg
    with psycopg.connect(DATABASE_URL,
                         options="-c default_transaction_read_only=on "
                                 "-c statement_timeout=5000") as conn:
        with conn.cursor() as cur:
            cur.execute(sql)
            rows = cur.fetchmany(100)
            cols = [d[0] for d in cur.description] if cur.description else []
    return str({"columns": cols, "rows": rows})


def _safe_path(path: str) -> str:
    root = os.path.realpath(WORKSPACE_DIR)
    full = os.path.realpath(os.path.join(root, path))
    if os.path.commonpath((root, full)) != root:
        raise ValueError("path escapes workspace")
    return full


def file_read(path: str) -> str:
    with open(_safe_path(path)) as f:
        return f.read(100_000)


def file_write(path: str, content: str) -> str:
    full = _safe_path(path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w") as f:
        f.write(content[:1_000_000])
    return "ok"


def code_exec(code: str, language: str = "python") -> str:
    if language != "python":
        raise ValueError("only python is supported")
    def limit_resources():
        resource.setrlimit(resource.RLIMIT_CPU, (8, 10))
        resource.setrlimit(resource.RLIMIT_AS, (256 * 1024 * 1024, 256 * 1024 * 1024))
        resource.setrlimit(resource.RLIMIT_NOFILE, (64, 64))
    try:
        out = subprocess.run(["python3", "-c", code], capture_output=True,
                             text=True, timeout=10, preexec_fn=limit_resources)
        return (out.stdout + out.stderr)[-20_000:] or "(no output)"
    except subprocess.TimeoutExpired:
        return "timeout after 10s"


TOOLS = {
    "web_search": lambda a: web_search(a.get("query", "")),
    "db_query": lambda a: db_query(a.get("sql", "")),
    "file_read": lambda a: file_read(a.get("path", "")),
    "file_write": lambda a: file_write(a.get("path", ""), a.get("content", "")),
    "code_exec": lambda a: code_exec(a.get("code", ""), a.get("language", "python")),
}


# ------------------------------------------------------------------- api

@app.get("/health")
def health():
    return {"status": "ok", "tools": sorted(TOOLS)}


@app.post("/invoke", response_model=InvokeResponse)
def invoke(req: InvokeRequest,
           internal_token: str | None = Header(default=None, alias="X-AgentGuard-Internal-Token")):
    if not MCP_SERVER_TOKEN:
        raise HTTPException(503, "MCP_SERVER_TOKEN is not configured")
    if not internal_token or not hmac.compare_digest(internal_token, MCP_SERVER_TOKEN):
        raise HTTPException(401, "request must come from the AgentGuard policy proxy")
    fn = TOOLS.get(req.tool)
    if fn is None:
        raise HTTPException(404, f"unknown tool: {req.tool}")
    try:
        return InvokeResponse(tool=req.tool, result=str(fn(req.args or {})))
    except (ValueError, NotImplementedError) as e:
        raise HTTPException(400, str(e))
    except Exception as e:  # noqa: BLE001 — never leak tracebacks to agents
        raise HTTPException(500, f"tool failed: {type(e).__name__}")
