'use client';

import { useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import Link from 'next/link';
import {
  api,
  fmtTime,
  shortId,
  type AnomalyCase,
  type Investigation,
} from '../../../lib/api';

export default function CaseDetailPage() {
  const params = useParams();
  const router = useRouter();
  const id = params.id as string;

  const [c, setC] = useState<AnomalyCase | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [investigation, setInvestigation] = useState<Investigation | null>(null);
  const [decision, setDecision] = useState('APPROVE');
  const [reason, setReason] = useState('');
  const [decided, setDecided] = useState(false);

  const load = () => {
    setLoading(true);
    api
      .getCase(id)
      .then(setC)
      .catch((err) =>
        setError(err instanceof Error ? err.message : 'Failed to load case'),
      )
      .finally(() => setLoading(false));
  };

  useEffect(load, [id]);

  const run = async (fn: () => Promise<unknown>, after?: () => void) => {
    setBusy(true);
    setError('');
    try {
      await fn();
      after?.();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Action failed');
    } finally {
      setBusy(false);
    }
  };

  const investigate = () =>
    run(async () => {
      const inv = await api.investigateCase(id);
      setInvestigation(inv);
    });

  const setStatus = (status: string) =>
    run(async () => {
      const updated = await api.setCaseStatus(id, status);
      setC(updated);
    });

  const submitDecision = (e: React.FormEvent) => {
    e.preventDefault();
    run(
      async () => {
        await api.decide({ caseId: id, decision, reason: reason || undefined });
        setDecided(true);
      },
    );
  };

  if (loading) return <div className="center muted">Loading…</div>;
  if (!c) return <div className="error">{error || 'Case not found.'}</div>;

  return (
    <>
      <p>
        <Link href="/cases">← Back to cases</Link>
      </p>
      <h1>
        Case <span className="mono">{shortId(c.id)}</span>
      </h1>
      {error && <div className="error">{error}</div>}

      <div className="card">
        <dl className="kv">
          <dt>Anomaly score</dt>
          <dd>
            <strong>{c.score.toFixed(3)}</strong>
          </dd>
          <dt>Status</dt>
          <dd>
            <span className={`badge ${c.status.toLowerCase()}`}>{c.status}</span>
          </dd>
          <dt>Agent</dt>
          <dd className="mono">{shortId(c.agentId)}</dd>
          <dt>Run</dt>
          <dd className="mono">{c.runId ? shortId(c.runId) : '—'}</dd>
          <dt>Opened</dt>
          <dd>{fmtTime(c.createdAt)}</dd>
        </dl>
      </div>

      <h2>Summary</h2>
      {c.summary ? (
        <div className="prewrap">{c.summary}</div>
      ) : (
        <p className="muted">
          No investigation summary yet — run Investigate below (Phase 4 RAG).
        </p>
      )}

      {investigation && (
        <div className="card">
          <h2 style={{ marginTop: 0 }}>
            Investigation <span className="muted">({investigation.provider})</span>
          </h2>
          <div className="prewrap">{investigation.summary}</div>
        </div>
      )}

      <h2>Triage</h2>
      <div className="row">
        <button className="secondary" disabled={busy} onClick={investigate}>
          {busy ? 'Working…' : 'Investigate'}
        </button>
        <button
          className="secondary"
          disabled={busy}
          onClick={() => setStatus('RESOLVED')}
        >
          Resolve
        </button>
        <button className="danger" disabled={busy} onClick={() => setStatus('DISMISSED')}>
          Dismiss
        </button>
      </div>

      <h2>Human decision</h2>
      {decided ? (
        <p className="muted">Decision recorded.</p>
      ) : (
        <form className="inline-form" onSubmit={submitDecision}>
          <div className="field">
            <label htmlFor="cdec">Decision</label>
            <select
              id="cdec"
              value={decision}
              onChange={(e) => setDecision(e.target.value)}
            >
              <option value="APPROVE">APPROVE</option>
              <option value="DENY">DENY</option>
            </select>
          </div>
          <div className="field">
            <label htmlFor="creason">Reason</label>
            <input
              id="creason"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="why"
              style={{ minWidth: 260 }}
            />
          </div>
          <button type="submit" disabled={busy}>
            Submit
          </button>
        </form>
      )}
    </>
  );
}
