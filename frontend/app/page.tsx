'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, fmtTime, shortId, type Agent, type AnomalyCase, type AuditLog } from '../lib/api';

export default function OverviewPage() {
  const [agents, setAgents] = useState<Agent[]>([]);
  const [cases, setCases] = useState<AnomalyCase[]>([]);
  const [audit, setAudit] = useState<AuditLog[]>([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [apiOnline, setApiOnline] = useState<boolean | null>(null);
  const [updatedAt, setUpdatedAt] = useState<Date | null>(null);

  const load = useCallback(async () => {
    setError('');
    const [health, agentResult, caseResult, auditResult] = await Promise.allSettled([
      api.health(), api.listAgents(), api.listCases('OPEN'), api.listAudit(),
    ]);
    setApiOnline(health.status === 'fulfilled' && health.value.status === 'ok');
    if (agentResult.status === 'fulfilled') setAgents(agentResult.value);
    if (caseResult.status === 'fulfilled') setCases(caseResult.value);
    if (auditResult.status === 'fulfilled') setAudit(auditResult.value.slice(0, 8));
    const failed = [agentResult, caseResult, auditResult].find((result) => result.status === 'rejected');
    if (failed?.status === 'rejected') setError(failed.reason instanceof Error ? failed.reason.message : 'Some workspace data could not be loaded.');
    setUpdatedAt(new Date());
    setLoading(false);
  }, []);

  useEffect(() => {
    void load();
    const timer = window.setInterval(() => {
      if (document.visibilityState === 'visible') void load();
    }, 60_000);
    return () => window.clearInterval(timer);
  }, [load]);

  if (loading) return <div className="center muted">Loading workspace…</div>;
  const activeAgents = agents.filter((agent) => agent.status?.toLowerCase() === 'active').length;
  const highRiskAgents = agents.filter((agent) => agent.riskTier?.toLowerCase() === 'high').length;

  return <>
    <div className="page-intro">
      <div><div className="eyebrow">Control room</div><h1>Overview</h1><p>Monitor agent activity, review exceptions, and keep decisions traceable.</p></div>
      <button className="secondary refresh-button" onClick={() => { setLoading(true); void load(); }}>↻ <span>Refresh</span></button>
    </div>
    {error && <div className="error">{error}</div>}
    <div className="health-card" role="status">
      <span className={`health-indicator ${apiOnline === false ? 'down' : ''}`} />
      <div><strong>{apiOnline === false ? 'Backend needs attention' : apiOnline ? 'All systems operational' : 'Checking services'}</strong><span>{apiOnline === false ? 'API health check failed. Workspace data may be stale.' : `Agent governance API · updated ${updatedAt ? updatedAt.toLocaleTimeString() : 'just now'}`}</span></div>
      <span className={`health-state ${apiOnline === false ? 'down' : ''}`}>{apiOnline === false ? 'DEGRADED' : apiOnline ? 'HEALTHY' : 'CHECKING'}</span>
    </div>
    <div className="stats four">
      <div className="stat"><div className="label">Agents</div><div className="value">{agents.length}</div><div className="trend"><span className="trend-dot" /> {activeAgents} active</div></div>
      <div className="stat"><div className="label">Open cases</div><div className="value">{cases.length}</div><div className="trend"><span className="trend-dot" style={{ background: cases.length ? 'var(--amber)' : 'var(--green)' }} /> {cases.length ? 'Needs review' : 'Queue clear'}</div></div>
      <div className="stat"><div className="label">High risk agents</div><div className="value">{highRiskAgents}</div><div className="trend">Across registered agents</div></div>
      <div className="stat"><div className="label">Audit events</div><div className="value">{audit.length}</div><div className="trend">Latest recorded activity</div></div>
    </div>
    <div className="dashboard-grid">
      <section className="card"><div className="section-heading"><h2>Recent activity</h2><a href="/audit">View audit log →</a></div>
        {audit.length === 0 ? <p className="muted">No audit entries yet.</p> : <div className="activity-list">{audit.slice(0, 6).map((item) => <div className="activity-item" key={item.id}><span className="activity-marker" /><div><strong>{item.action}</strong><small>{item.actor} · {shortId(item.target)}</small></div><small>{fmtTime(item.createdAt)}</small></div>)}</div>}
      </section>
      <section className="card"><div className="section-heading"><h2>Case queue</h2><a href="/cases">View all →</a></div>
        {cases.length === 0 ? <p className="muted">No open cases. The queue is clear.</p> : <div className="activity-list">{cases.slice(0, 6).map((item) => <a className="activity-item case-activity" href={`/cases/${item.id}`} key={item.id}><span className="activity-marker" style={{ background: item.score >= .7 ? 'var(--red)' : 'var(--amber)' }} /><div><strong>Case {shortId(item.id)}</strong><small>Agent {shortId(item.agentId)}</small></div><span className="badge open">{item.score.toFixed(2)}</span></a>)}</div>}
      </section>
    </div>
    <div className="page-intro" style={{ margin: '24px 0 12px' }}><div><h2 style={{ margin: 0 }}>Audit trail</h2><p>Append-only record of workspace actions.</p></div><a href="/audit">Open full log →</a></div>
    {audit.length === 0 ? <p className="muted">No audit entries yet.</p> : <div className="table-wrap"><table><thead><tr><th>Time</th><th>Actor</th><th>Action</th><th>Target</th></tr></thead><tbody>{audit.slice(0, 5).map((item) => <tr key={item.id}><td>{fmtTime(item.createdAt)}</td><td className="mono">{item.actor}</td><td>{item.action}</td><td className="mono">{shortId(item.target)}</td></tr>)}</tbody></table></div>}
  </>;
}
