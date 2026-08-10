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
import InvoicesPage from './pages/InvoicesPage';
import BulkPayoutsPage from './pages/BulkPayoutsPage';

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
   OAuth Callback Handler
   Exchanges the authorization code for a JWT token.
   ────────────────────────────────────────────────── */
function OAuthCallbackPage() {
  const [status, setStatus] = React.useState<'loading' | 'error'>('loading');
  const [errorMsg, setErrorMsg] = React.useState('');

  React.useEffect(() => {
    const params = new URLSearchParams(location.search);
    const code = params.get('code');
    const state = params.get('state');
    const error = params.get('error');
    const errorDesc = params.get('error_description');

    if (error) {
      setErrorMsg(`Auth server error: ${errorDesc ?? error}`);
      setStatus('error');
      return;
    }

    const savedState = sessionStorage.getItem('oauth_state');
    const verifier = sessionStorage.getItem('pkce_verifier');

    if (!code || !state || state !== savedState || !verifier) {
      setErrorMsg('Invalid or mismatched OAuth state. Please try logging in again.');
      setStatus('error');
      return;
    }

    sessionStorage.removeItem('oauth_state');
    sessionStorage.removeItem('pkce_verifier');

    const base = import.meta.env.VITE_AUTH_BASE_URL || 'http://localhost:8081';
    const clientId = import.meta.env.VITE_OAUTH_CLIENT_ID || 'openfloat-staff-portal';
    const redirectUri = `${location.origin}/oauth/callback`;

    const body = new URLSearchParams({
      grant_type: 'authorization_code',
      code,
      redirect_uri: redirectUri,
      client_id: clientId,
      code_verifier: verifier,
    });

    fetch(`${base}/oauth2/token`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: body.toString(),
    })
      .then(async (res) => {
        if (!res.ok) {
          const text = await res.text();
          throw new Error(`Token exchange failed (${res.status}): ${text}`);
        }
        return res.json();
      })
      .then((data) => {
        const token = data.access_token;
        if (!token) throw new Error('No access_token in response');
        localStorage.setItem('openfloat.access_token', token);
        // Decode role from JWT claims if present
        try {
          const payload = JSON.parse(atob(token.split('.')[1]));
          const role = payload.role ?? payload.roles?.[0] ?? 'STAFF';
          localStorage.setItem('openfloat.role', role);
        } catch {
          localStorage.setItem('openfloat.role', 'STAFF');
        }
        location.replace('/');
      })
      .catch((err: Error) => {
        setErrorMsg(err.message);
        setStatus('error');
      });
  }, []);

  if (status === 'loading') {
    return (
      <div className="login-page">
        <div className="card login-card">
          <div className="loading-state"><span className="spinner" /> Completing sign-in…</div>
        </div>
      </div>
    );
  }

  return (
    <div className="login-page">
      <div className="card login-card">
        <h1 style={{ color: 'var(--color-error)', fontSize: '1.25rem' }}>Authentication Failed</h1>
        <p style={{ color: 'var(--color-text-secondary)', margin: '1rem 0' }}>{errorMsg}</p>
        <button className="btn btn-primary" onClick={() => location.replace('/login')}>Back to Login</button>
      </div>
    </div>
  );
}

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
  { key: 'invoices', label: 'Invoices', allowedRoles: ['ADMIN', 'MANAGER', 'FINANCE'] },
  { key: 'bulkpayouts', label: 'Bulk Payouts', allowedRoles: ['ADMIN', 'MANAGER', 'FINANCE'] },
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
    case 'invoices':
      return <InvoicesPage />;
    case 'bulkpayouts':
      return <BulkPayoutsPage />;
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
function Root() {
  // Intercept OAuth callback before rendering the full shell
  if (location.pathname === '/oauth/callback') {
    return <OAuthCallbackPage />;
  }
  return <AppShell />;
}

createRoot(document.getElementById('root')!).render(
  <QueryClientProvider client={queryClient}>
    <Root />
  </QueryClientProvider>
);
