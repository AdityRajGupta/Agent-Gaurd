// Typed client for the AgentGuard backend API.
// Explicit environment configuration always wins. The production fallback keeps
// a misconfigured Vercel build from silently sending users to localhost.
// JWT is stored in localStorage under 'ag_token' and sent as
// `Authorization: Bearer <token>` on every request.

const API_URL = process.env.NEXT_PUBLIC_API_URL ||
  (process.env.NODE_ENV === 'production'
    ? 'https://agentguard-backend-9d3x.onrender.com/api/v1'
    : 'http://localhost:8080/api/v1');

export function getToken(): string | null {
  if (typeof window === 'undefined') return null;
  return localStorage.getItem('ag_token');
}

export function setToken(token: string): void {
  localStorage.setItem('ag_token', token);
}

export function clearToken(): void {
  localStorage.removeItem('ag_token');
}

export async function apiFetch<T = unknown>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const token = getToken();
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...((options.headers as Record<string, string> | undefined) ?? {}),
  };
  if (token) headers['Authorization'] = `Bearer ${token}`;

  let res: Response;
  try {
    res = await fetch(`${API_URL}${path}`, {
      ...options,
      headers,
      cache: 'no-store',
      signal: options.signal ?? AbortSignal.timeout(90_000),
    });
  } catch (e) {
    if (e instanceof Error && (e.name === 'TimeoutError' || e.name === 'AbortError')) {
      throw new Error('The API is taking too long to respond. It may be waking from an idle period — please try again shortly.');
    }
    throw new Error(
      `Cannot reach the API at ${API_URL} — is the backend running?`,
    );
  }
  if (res.status === 401) {
    clearToken();
    if (typeof window !== 'undefined') window.location.href = '/login';
    throw new Error('Session expired — please log in again.');
  }
  if (!res.ok) {
    const text = await res.text().catch(() => '');
    let message = text;
    try {
      const body = JSON.parse(text);
      message = body.message || body.error || text;
    } catch { /* Keep plain-text API errors readable. */ }
    throw new Error(message || `Request failed (HTTP ${res.status})`);
  }
  const text = await res.text();
  return (text ? JSON.parse(text) : null) as T;
}

// ------------------------------------------------------------------ types

export interface Agent {
  id: string;
  name: string;
  declaredTask: string;
  riskTier: string;
  status: string;
  createdAt: string;
}

export interface ToolDef {
  id: string;
  name: string;
  description: string;
  dataSensitivity: string;
}

export interface ToolEvent {
  id: string;
  eventId: string;
  agentId: string;
  toolId: string;
  policyDecision: string;
  anomalyScore: number | null;
  createdAt: string;
}

export interface Policy {
  id: string;
  agentId: string;
  toolId: string;
  effect: string;
  maxCalls: number | null;
}

export interface Run {
  id: string;
  agentId: string;
  status: string;
  startedAt?: string;
  endedAt?: string | null;
}

export interface AnomalyCase {
  id: string;
  agentId: string;
  runId: string | null;
  score: number;
  status: string;
  summary: string | null;
  createdAt: string;
}

export interface AuditLog {
  id: string;
  actor: string;
  action: string;
  target: string;
  createdAt: string;
}

export interface Investigation {
  summary: string;
  provider: string;
}

// --------------------------------------------------------------- helpers

export const api = {
  login: (username: string, password: string): Promise<{ token: string }> =>
    apiFetch('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username: username.trim(), password }),
    }),

  health: (): Promise<{ status: string; service: string }> =>
    apiFetch('/health'),

  listAgents: (): Promise<Agent[]> => apiFetch('/agents'),

  createAgent: (data: {
    name: string;
    declaredTask: string;
    riskTier: string;
  }): Promise<Agent> =>
    apiFetch('/agents', { method: 'POST', body: JSON.stringify(data) }),

  listTools: (): Promise<ToolDef[]> => apiFetch('/tools'),

  agentEvents: (agentId: string): Promise<ToolEvent[]> =>
    apiFetch(`/agents/${agentId}/events`),

  agentPolicies: (agentId: string): Promise<Policy[]> =>
    apiFetch(`/policies/agent/${agentId}`),

  createPolicy: (data: {
    agentId: string;
    toolId: string;
    effect: string;
    maxCalls?: number | null;
  }): Promise<Policy> =>
    apiFetch('/policies', { method: 'POST', body: JSON.stringify(data) }),

  startRun: (agentId: string): Promise<Run> =>
    apiFetch('/runs', { method: 'POST', body: JSON.stringify({ agentId }) }),

  getRun: (runId: string): Promise<Run> => apiFetch(`/runs/${runId}`),

  listCases: (status?: string): Promise<AnomalyCase[]> =>
    apiFetch(status ? `/cases?status=${status}` : '/cases'),

  getCase: (caseId: string): Promise<AnomalyCase> =>
    apiFetch(`/cases/${caseId}`),

  investigateCase: (caseId: string): Promise<Investigation> =>
    apiFetch(`/cases/${caseId}/investigate`, { method: 'POST' }),

  setCaseStatus: (caseId: string, status: string): Promise<AnomalyCase> =>
    apiFetch(`/cases/${caseId}`, {
      method: 'PATCH',
      body: JSON.stringify({ status }),
    }),

  decide: (data: {
    eventId?: string;
    caseId?: string;
    decision: string;
    reason?: string;
  }): Promise<unknown> =>
    apiFetch('/decisions', { method: 'POST', body: JSON.stringify(data) }),

  listAudit: (): Promise<AuditLog[]> => apiFetch('/audit'),
};

export function shortId(id: string): string {
  return id ? id.slice(0, 8) : '';
}

export function fmtTime(iso: string): string {
  if (!iso) return '';
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleString();
}
