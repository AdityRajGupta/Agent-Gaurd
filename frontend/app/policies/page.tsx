'use client';

import { useEffect, useState } from 'react';
import {
  api,
  shortId,
  type Agent,
  type Policy,
  type ToolDef,
} from '../../lib/api';

export default function PoliciesPage() {
  const [agents, setAgents] = useState<Agent[]>([]);
  const [tools, setTools] = useState<ToolDef[]>([]);
  const [policies, setPolicies] = useState<Policy[]>([]);
  const [agentId, setAgentId] = useState('');
  const [error, setError] = useState('');
  const [loadingPolicies, setLoadingPolicies] = useState(false);

  const [toolId, setToolId] = useState('');
  const [effect, setEffect] = useState('ALLOW');
  const [maxCalls, setMaxCalls] = useState('');
  const [creating, setCreating] = useState(false);

  useEffect(() => {
    Promise.all([api.listAgents(), api.listTools()])
      .then(([a, t]) => {
        setAgents(a);
        setTools(t);
        if (a.length > 0) setAgentId(a[0].id);
        if (t.length > 0) setToolId(t[0].id);
      })
      .catch((err) =>
        setError(err instanceof Error ? err.message : 'Failed to load'),
      );
  }, []);

  useEffect(() => {
    if (!agentId) return;
    setLoadingPolicies(true);
    api
      .agentPolicies(agentId)
      .then(setPolicies)
      .catch((err) =>
        setError(err instanceof Error ? err.message : 'Failed to load policies'),
      )
      .finally(() => setLoadingPolicies(false));
  }, [agentId]);

  const toolName = (id: string) =>
    tools.find((t) => t.id === id)?.name ?? shortId(id);

  const create = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!agentId || !toolId) return;
    setCreating(true);
    setError('');
    try {
      await api.createPolicy({
        agentId,
        toolId,
        effect,
        maxCalls: maxCalls === '' ? null : Number(maxCalls),
      });
      setMaxCalls('');
      const p = await api.agentPolicies(agentId);
      setPolicies(p);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create policy');
    } finally {
      setCreating(false);
    }
  };

  return (
    <>
      <h1>Policies</h1>
      {error && <div className="error">{error}</div>}

      <div className="card">
        <div className="field">
          <label htmlFor="pagent">Agent</label>
          <select
            id="pagent"
            value={agentId}
            onChange={(e) => setAgentId(e.target.value)}
          >
            {agents.map((a) => (
              <option key={a.id} value={a.id}>
                {a.name} ({shortId(a.id)})
              </option>
            ))}
          </select>
        </div>
      </div>

      <h2>Current policies</h2>
      {loadingPolicies ? (
        <p className="muted">Loading…</p>
      ) : policies.length === 0 ? (
        <p className="muted">
          No policies for this agent — the proxy default-denies everything.
        </p>
      ) : (
        <div className="table-wrap"><table>
          <thead>
            <tr>
              <th>Tool</th>
              <th>Effect</th>
              <th>Max calls</th>
            </tr>
          </thead>
          <tbody>
            {policies.map((p) => (
              <tr key={p.id}>
                <td className="mono">{toolName(p.toolId)}</td>
                <td>
                  <span className={`badge ${p.effect.toLowerCase()}`}>
                    {p.effect}
                  </span>
                </td>
                <td>{p.maxCalls ?? 'unlimited'}</td>
              </tr>
            ))}
          </tbody>
        </table></div>
      )}

      <h2>Add policy</h2>
      <div className="card">
        <form className="inline-form" onSubmit={create}>
          <div className="field">
            <label htmlFor="ptool">Tool</label>
            <select
              id="ptool"
              value={toolId}
              onChange={(e) => setToolId(e.target.value)}
            >
              {tools.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name} ({t.dataSensitivity})
                </option>
              ))}
            </select>
          </div>
          <div className="field">
            <label htmlFor="peffect">Effect</label>
            <select
              id="peffect"
              value={effect}
              onChange={(e) => setEffect(e.target.value)}
            >
              <option value="ALLOW">ALLOW</option>
              <option value="DENY">DENY</option>
              <option value="APPROVE">APPROVE</option>
            </select>
          </div>
          <div className="field">
            <label htmlFor="pmax">Max calls (optional)</label>
            <input
              id="pmax"
              type="number"
              min={1}
              value={maxCalls}
              onChange={(e) => setMaxCalls(e.target.value)}
              placeholder="unlimited"
            />
          </div>
          <button type="submit" disabled={creating || !agentId || !toolId}>
            {creating ? 'Creating…' : 'Create policy'}
          </button>
        </form>
      </div>
    </>
  );
}
