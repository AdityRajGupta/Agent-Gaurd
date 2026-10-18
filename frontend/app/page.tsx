'use client';

import { useEffect, useState } from 'react';
import { api, fmtTime, shortId, type Agent, type AnomalyCase, type AuditLog } from '../lib/api';

export default function OverviewPage() {
  const [agents, setAgents] = useState<Agent[]>([]);
  const [cases, setCases] = useState<AnomalyCase[]>([]);
  const [audit, setAudit] = useState<AuditLog[]>([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.all([api.listAgents(), api.listCases('OPEN'), api.listAudit()])
      .then(([a, c, au]) => {
        setAgents(a);
        setCases(c);
        setAudit(au.slice(0, 10));
      })
      .catch((err) => setError(err instanceof Error ? err.message : 'Failed to load'))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <div className="center muted">Loading…</div>;

  return (
    <>
      <div className="page-intro">
        <div>
          <div className="eyebrow">Control room</div>
          <h1>Overview</h1>
          <p>Monitor agent activity, review exceptions, and keep decisions traceable.</p>
        </div>
        <span className="workspace-pill"><span className="workspace-dot" /> Governance active</span>
      </div>
      {error && <div className="error">{error}</div>}
      <div className="stats">
        <div className="stat">
          <div className="label">Agents</div>
          <div className="value">{agents.length}</div>
        </div>
        <div className="stat">
          <div className="label">Open cases</div>
          <div className="value">{cases.length}</div>
        </div>
      </div>

      <div className="page-intro" style={{ marginBottom: 12 }}>
        <div><h2 style={{ margin: 0 }}>Recent audit entries</h2><p>Latest recorded actions across your workspace.</p></div>
        <a href="/audit">View audit log →</a>
      </div>
      {audit.length === 0 ? (
        <p className="muted">No audit entries yet.</p>
      ) : (
        <div className="table-wrap"><table>
          <thead>
            <tr>
              <th>Time</th>
              <th>Actor</th>
              <th>Action</th>
              <th>Target</th>
            </tr>
          </thead>
          <tbody>
            {audit.map((a) => (
              <tr key={a.id}>
                <td>{fmtTime(a.createdAt)}</td>
                <td className="mono">{a.actor}</td>
                <td>{a.action}</td>
                <td className="mono">{shortId(a.target)}</td>
              </tr>
            ))}
          </tbody>
        </table></div>
      )}
    </>
  );
}
