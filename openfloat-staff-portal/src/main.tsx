import React from 'react';
import { createRoot } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { auth } from './api/client';

import LoginPage from './pages/LoginPage';
import DashboardPage from './pages/DashboardPage';
import PaymentInitiatePage from './pages/PaymentInitiatePage';
import TransactionsPage from './pages/TransactionsPage';
import AuditLogPage from './pages/AuditLogPage';

import './styles.css';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      retry: 1,
    },
  },
});

/* ──────────────────────────────────────────────────
   Navigation Config
   ────────────────────────────────────────────────── */
interface NavItem {
  key: string;
  label: string;
  icon: string;
  adminOnly?: boolean;
}

const NAV_ITEMS: NavItem[] = [
  { key: 'dashboard', label: 'Dashboard', icon: '📊' },
  { key: 'payments', label: 'Payments', icon: '💳' },
  { key: 'transactions', label: 'Transactions', icon: '📋' },
  { key: 'audit', label: 'Audit Log', icon: '🔍', adminOnly: true },
  { key: 'users', label: 'Users', icon: '👥', adminOnly: true },
  { key: 'settings', label: 'Settings', icon: '⚙️', adminOnly: true },
];

/* ──────────────────────────────────────────────────
   App Shell
   ────────────────────────────────────────────────── */
function AppShell() {
  const [page, setPage] = React.useState(() => {
    const path = location.pathname.slice(1);
    return path || 'dashboard';
  });

  // Redirect unauthenticated users to login
  if (!auth.isAuthenticated() || page === 'login') {
    return <LoginPage />;
  }

  const role = auth.getRole();
  const visibleNav = NAV_ITEMS.filter(
    (item) => !item.adminOnly || role === 'ADMIN'
  );

  return (
    <div className="app-shell">
      {/* ─── Sidebar ─── */}
      <aside className="sidebar">
        <div className="sidebar-brand">
          <h1>OpenFloat</h1>
          <span>M-Pesa Operations</span>
        </div>

        <nav className="sidebar-nav">
          {visibleNav.map((item) => (
            <button
              key={item.key}
              className={page === item.key ? 'active' : ''}
              onClick={() => setPage(item.key)}
            >
              <span className="nav-icon">{item.icon}</span>
              {item.label}
            </button>
          ))}
        </nav>

        <div className="sidebar-signout">
          <button onClick={auth.logout}>↪ Sign Out</button>
        </div>
      </aside>

      {/* ─── Main Content ─── */}
      <main className="main-content">
        <header className="page-header">
          <p>Secure gateway console</p>
          <h2>{NAV_ITEMS.find((n) => n.key === page)?.label ?? page}</h2>
        </header>

        <PageRouter page={page} />
      </main>
    </div>
  );
}

/* ──────────────────────────────────────────────────
   Page Router
   ────────────────────────────────────────────────── */
function PageRouter({ page }: { page: string }) {
  switch (page) {
    case 'dashboard':
      return <DashboardPage />;
    case 'payments':
      return <PaymentInitiatePage />;
    case 'transactions':
      return <TransactionsPage />;
    case 'audit':
      return <AuditLogPage />;
    case 'users':
      return <UsersStub />;
    case 'settings':
      return <SettingsStub />;
    default:
      return <DashboardPage />;
  }
}

/* ──────────────────────────────────────────────────
   Stub Pages (to be replaced in final 25%)
   ────────────────────────────────────────────────── */

function UsersStub() {
  return (
    <div className="card">
      <h3>User Management</h3>
      <p style={{ color: 'var(--color-text-muted)' }}>
        Admin-only user CRUD interface — coming next.
      </p>
    </div>
  );
}

function SettingsStub() {
  return (
    <div className="card">
      <h3>Settings</h3>
      <p style={{ color: 'var(--color-text-muted)' }}>
        Paybill configuration and API client management — coming next.
      </p>
    </div>
  );
}

/* ──────────────────────────────────────────────────
   Mount
   ────────────────────────────────────────────────── */
createRoot(document.getElementById('root')!).render(
  <QueryClientProvider client={queryClient}>
    <AppShell />
  </QueryClientProvider>
);
