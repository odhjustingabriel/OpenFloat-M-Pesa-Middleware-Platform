import React from 'react';
import { createRoot } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { auth } from './api/client';

import LoginPage from './pages/LoginPage';
import DashboardPage from './pages/DashboardPage';
import PaymentInitiatePage from './pages/PaymentInitiatePage';
import TransactionsPage from './pages/TransactionsPage';
import AuditLogPage from './pages/AuditLogPage';
import UserManagementPage from './pages/UserManagementPage';
import SettingsPage from './pages/SettingsPage';
import ClientManagementPage from './pages/ClientManagementPage';
import AccountReferencesPage from './pages/AccountReferencesPage';
import WebhookLogsPage from './pages/WebhookLogsPage';
import ReconciliationPage from './pages/ReconciliationPage';

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
  allowedRoles?: string[];
}

const NAV_ITEMS: NavItem[] = [
  { key: 'dashboard', label: 'Dashboard' },
  { key: 'payments', label: 'Payments' },
  { key: 'transactions', label: 'Transactions' },
  { key: 'clients', label: 'Client Apps', allowedRoles: ['ADMIN', 'MANAGER'] },
  { key: 'references', label: 'Account References', allowedRoles: ['ADMIN', 'MANAGER'] },
  { key: 'webhooks', label: 'Webhook Logs', allowedRoles: ['ADMIN', 'MANAGER'] },
  { key: 'reconciliation', label: 'Reconciliation', allowedRoles: ['ADMIN', 'MANAGER', 'FINANCE'] },
  { key: 'audit', label: 'Audit Log', allowedRoles: ['ADMIN'] },
  { key: 'users', label: 'Users', allowedRoles: ['ADMIN'] },
  { key: 'settings', label: 'Settings', allowedRoles: ['ADMIN'] },
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
    (item) => !item.allowedRoles || item.allowedRoles.includes(role)
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
              {item.label}
            </button>
          ))}
        </nav>

        <div className="sidebar-signout">
          <button onClick={auth.logout}>Sign Out</button>
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
    case 'clients':
      return <ClientManagementPage />;
    case 'references':
      return <AccountReferencesPage />;
    case 'webhooks':
      return <WebhookLogsPage />;
    case 'reconciliation':
      return <ReconciliationPage />;
    case 'audit':
      return <AuditLogPage />;
    case 'users':
      return <UserManagementPage />;
    case 'settings':
      return <SettingsPage />;
    default:
      return <DashboardPage />;
  }
}

/* ──────────────────────────────────────────────────
   Mount
   ────────────────────────────────────────────────── */
createRoot(document.getElementById('root')!).render(
  <QueryClientProvider client={queryClient}>
    <AppShell />
  </QueryClientProvider>
);
