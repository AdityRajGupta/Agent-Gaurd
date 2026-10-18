'use client';

import { useEffect, useState } from 'react';
import { api, fmtTime, shortId, type Agent } from '../../lib/api';

export default function AgentsPage() {
  const [agents, setAgents] = useState<Agent[]>([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [name, setName] = useState('');
  const [declaredTask, setDeclaredTask] = useState('');
  const [riskTier, setRiskTier] = useState('standard');
  const [creating, setCreating] = useState(false);

  const load = () => {
    setLoading(true);
    api
      .listAgents()
      .then(setAgents)
      .catch((err) =>
        setError(err instanceof Error ? err.message : 'Failed to load agents'),
      )
      .finally(() => setLoading(false));
  };

  useEffect(load, []);

  const create = async (e: React.FormEvent) => {
    e.preventDefault();
    setCreating(true);
    setError('');
    try {
      await api.createAgent({ name, declaredTask, riskTier });
      setName('');
      setDeclaredTask('');
      setRiskTier('standard');
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create agent');
    } finally {
      setCreating(false);
    }
  };

  return (
    <>
      <h1>Agents</h1>
      {error && <div className="error">{error}</div>}

      <div className="card">
        <h2 style={{ marginTop: 0 }}>Register agent</h2>
        <form className="inline-form" onSubmit={create}>
          <div className="field">
            <label htmlFor="aname">Name</label>
            <input
              id="aname"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
            />
          </div>
          <div className="field">
            <label htmlFor="atask">Declared task</label>
            <input
              id="atask"
              value={declaredTask}
              onChange={(e) => setDeclaredTask(e.target.value)}
              style={{ minWidth: 260 }}
            />
          </div>
          <div className="field">
            <label htmlFor="atier">Risk tier</label>
            <select
              id="atier"
              value={riskTier}
              onChange={(e) => setRiskTier(e.target.value)}
            >
              <option value="low">low</option>
              <option value="standard">standard</option>
              <option value="high">high</option>
            </select>
          </div>
          <button type="submit" disabled={creating}>
            {creating ? 'Creating…' : 'Create'}
          </button>
        </form>
      </div>

      {loading ? (
        <p className="muted">Loading…</p>
      ) : agents.length === 0 ? (
        <p className="muted">No agents registered yet.</p>
      ) : (
        <div className="table-wrap"><table>
          <thead>
            <tr>
              <th>ID</th>
              <th>Name</th>
              <th>Declared task</th>
              <th>Risk tier</th>
              <th>Status</th>
              <th>Created</th>
            </tr>
          </thead>
          <tbody>
            {agents.map((a) => (
              <tr key={a.id}>
                <td className="mono">{shortId(a.id)}</td>
                <td>{a.name}</td>
                <td>{a.declaredTask}</td>
                <td>
                  <span className={`badge ${a.riskTier}`}>{a.riskTier}</span>
                </td>
                <td>{a.status}</td>
                <td>{fmtTime(a.createdAt)}</td>
              </tr>
            ))}
          </tbody>
        </table></div>
      )}
    </>
  );
}
