'use client';

import { useEffect, useState } from 'react';
import { api, fmtTime, shortId, type AuditLog } from '../../lib/api';

export default function AuditPage() {
  const [entries, setEntries] = useState<AuditLog[]>([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api
      .listAudit()
      .then(setEntries)
      .catch((err) =>
        setError(err instanceof Error ? err.message : 'Failed to load audit log'),
      )
      .finally(() => setLoading(false));
  }, []);

  return (
    <>
      <h1>Audit log</h1>
      <p className="muted">Append-only — entries can be added, never changed.</p>
      {error && <div className="error">{error}</div>}
      {loading ? (
        <p className="muted">Loading…</p>
      ) : entries.length === 0 ? (
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
            {entries.map((a) => (
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
