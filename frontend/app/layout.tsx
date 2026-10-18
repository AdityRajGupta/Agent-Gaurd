import './globals.css';
import ClientShell from './components/ClientShell';

export const metadata = {
  title: 'AgentGuard',
  description: 'Governance dashboard for autonomous AI agents',
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en">
      <body>
        <ClientShell>{children}</ClientShell>
      </body>
    </html>
  );
}
