'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { api, setToken } from '../../lib/api';

export default function LoginPage() {
  const router = useRouter();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [slow, setSlow] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setBusy(true);
    setSlow(false);
    const slowHint = window.setTimeout(() => setSlow(true), 5000);
    try {
      const { token } = await api.login(username, password);
      setToken(token);
      router.push('/');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Login failed');
    } finally {
      window.clearTimeout(slowHint);
      setBusy(false);
      setSlow(false);
    }
  };

  return (
    <div className="login-wrap">
      <div className="login-brand"><span className="brand-mark">A</span>AgentGuard</div>
      <p className="login-subtitle">Sign in to your governance workspace</p>
      <form className="card" onSubmit={submit}>
        <div className="field">
          <label htmlFor="username">Username</label>
          <input
            id="username"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            autoComplete="username"
            autoFocus
            required
          />
        </div>
        <div className="field">
          <label htmlFor="password">Password</label>
          <input
            id="password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
            required
          />
        </div>
        {slow && <p className="login-wait" role="status">Waking the workspace can take up to a minute when it has been idle.</p>}
        {error && <div className="error" role="alert">{error}</div>}
        <button type="submit" disabled={busy}>
          {busy ? 'Connecting…' : 'Log in'}
        </button>
      </form>
    </div>
  );
}
