'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { api, fmtTime, shortId, type AnomalyCase } from '../../lib/api';

export default function CasesPage() {
  const router = useRouter();
  const [cases, setCases] = useState<AnomalyCase[]>([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api
      .listCases('OPEN')
      .then(setCases)
      .catch((err) =>
        setError(err instanceof Error ? err.message : 'Failed to load cases'),
      )
      .finally(() => setLoading(false));
  }, []);

  return (
    <>
      <h1>Open cases</h1>
      {error && <div className="error">{error}</div>}
      {loading ? (
        <p className="muted">Loading…</p>
      ) : cases.length === 0 ? (
        <p className="muted">No open cases. The queue stays quiet on benign runs.</p>
      ) : (
        <div className="table-wrap"><table>
          <thead>
            <tr>
              <th>Case</th>
              <th>Score</th>
              <th>Agent</th>
              <th>Status</th>
              <th>Opened</th>
            </tr>
          </thead>
          <tbody>
            {cases.map((c) => (
              <tr
                key={c.id}
                className="clickable"
                onClick={() => router.push(`/cases/${c.id}`)}
              >
                <td className="mono">{shortId(c.id)}</td>
                <td>
                  <strong>{c.score.toFixed(3)}</strong>
                </td>
                <td className="mono">{shortId(c.agentId)}</td>
                <td>
                  <span className="badge open">{c.status}</span>
                </td>
                <td>{fmtTime(c.createdAt)}</td>
              </tr>
            ))}
          </tbody>
        </table></div>
      )}
    </>
  );
}
