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
  const [paletteOpen, setPaletteOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [selectedIndex, setSelectedIndex] = useState(0);
  const filteredLinks = LINKS.filter((link) => link.label.toLowerCase().includes(query.toLowerCase()));

  useEffect(() => {
    if (!getToken() && pathname !== '/login') {
      router.replace('/login');
    } else {
      setReady(true);
    }
  }, [pathname, router]);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault();
        setPaletteOpen((open) => !open);
      } else if (event.key === 'Escape') {
        setPaletteOpen(false);
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, []);

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
            <button className="quick-search" onClick={() => setPaletteOpen(true)} aria-label="Open quick navigation"><span>⌕</span> Jump to… <kbd>⌘ K</kbd></button>
            <span className="workspace-pill"><span className="workspace-dot" /> Governance active</span>
            <button className="linkbtn account-button" onClick={logout}>Sign out</button>
          </header>
          <aside className="sidebar">
            <button className="workspace-switcher" onClick={() => setPaletteOpen(true)}><span className="workspace-avatar">AG</span><span><strong>AgentGuard</strong><small>Workspace</small></span><span className="switch-chevron">⌄</span></button>
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
          {paletteOpen && <div className="palette-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) setPaletteOpen(false); }}>
            <section className="command-palette" role="dialog" aria-modal="true" aria-label="Quick navigation">
              <div className="palette-search"><span>⌕</span><input autoFocus placeholder="Search pages…" value={query} onChange={(event) => { setQuery(event.target.value); setSelectedIndex(0); }} onKeyDown={(event) => { if (event.key === 'ArrowDown') { event.preventDefault(); setSelectedIndex((index) => Math.min(index + 1, filteredLinks.length - 1)); } else if (event.key === 'ArrowUp') { event.preventDefault(); setSelectedIndex((index) => Math.max(0, index - 1)); } else if (event.key === 'Enter' && filteredLinks[selectedIndex]) { router.push(filteredLinks[selectedIndex].href); setPaletteOpen(false); setQuery(''); } }} /><kbd>ESC</kbd></div>
              <div className="palette-label">Navigate</div>
              {filteredLinks.map((link, index) => <Link key={link.href} href={link.href} className={`palette-item ${index === selectedIndex ? 'selected' : ''}`} onMouseEnter={() => setSelectedIndex(index)} onClick={() => { setPaletteOpen(false); setQuery(''); }}><span className="nav-icon">{link.icon}</span>{link.label}<kbd>↵</kbd></Link>)}
              <div className="palette-foot">↑↓ Navigate <span>↵ Open</span></div>
            </section>
          </div>}
        </>
      )}
      <main className={onLoginPage ? 'container' : 'app-main'}>{onLoginPage ? children : <div className="container">{children}</div>}</main>
    </div>
  );
}
