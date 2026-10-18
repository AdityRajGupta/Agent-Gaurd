'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { getToken, clearToken } from '../../lib/api';

const LINKS = [
  { href: '/', label: 'Overview', icon: '◫' },
  { href: '/agents', label: 'Agents', icon: '◉' },
  { href: '/cases', label: 'Cases', icon: '◇' },
  { href: '/policies', label: 'Policies', icon: '⌘' },
  { href: '/audit', label: 'Audit log', icon: '≋' },
];

export default function ClientShell({
  children,
}: {
  children: React.ReactNode;
}) {
  const pathname = usePathname();
  const router = useRouter();
  const [ready, setReady] = useState(false);

  useEffect(() => {
    if (!getToken() && pathname !== '/login') {
      router.replace('/login');
    } else {
      setReady(true);
    }
  }, [pathname, router]);

  const onLoginPage = pathname === '/login';

  const logout = () => {
    clearToken();
    router.replace('/login');
  };

  if (!onLoginPage && !ready) {
    return <div className="center muted">Loading…</div>;
  }

  return (
    <div className="app-frame">
      {!onLoginPage && (
        <>
          <header className="topnav">
            <Link href="/" className="brand"><span className="brand-mark">A</span>AgentGuard</Link>
            <span className="topbar-context">/ Governance workspace</span>
            <span className="spacer" />
            <span className="workspace-pill"><span className="workspace-dot" /> Local workspace</span>
            <button className="linkbtn account-button" onClick={logout}>Sign out</button>
          </header>
          <aside className="sidebar">
            <div className="sidebar-label">Workspace</div>
            <nav className="side-links" aria-label="Main navigation">
              {LINKS.map((l) => (
                <Link key={l.href} href={l.href} className={
                  pathname === l.href || (l.href !== '/' && pathname.startsWith(l.href)) ? 'active' : ''
                }>
                  <span className="nav-icon" aria-hidden="true">{l.icon}</span>{l.label}
                </Link>
              ))}
            </nav>
            <div className="side-bottom"><strong>AgentGuard</strong><br />Policy at every tool call</div>
          </aside>
        </>
      )}
      <main className={onLoginPage ? 'container' : 'app-main'}>{onLoginPage ? children : <div className="container">{children}</div>}</main>
    </div>
  );
}
