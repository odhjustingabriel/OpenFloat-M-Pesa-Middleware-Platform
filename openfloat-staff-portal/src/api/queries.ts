import { api } from './client';
import type { Transaction, User, AuditLogEntry } from '../types/domain';

/* ── Transactions ─────────────────────────────────── */

export async function fetchTransactions(
  params: Record<string, string | number | undefined> = {}
): Promise<Transaction[]> {
  const { data } = await api.get('/api/v1/transactions', { params });
  return data.data?.content ?? data.data?.items ?? ([] as Transaction[]);
}

export async function fetchTransaction(id: string): Promise<Transaction> {
  const { data } = await api.get(`/api/v1/transactions/${id}`);
  return data.data as Transaction;
}

/* ── Payments ─────────────────────────────────────── */

export interface StkPushPayload {
  msisdn: string;
  amount: number;
  accountReference: string;
  paybill: string;
}

export interface StkPushResult {
  transactionId: string;
  checkoutRequestId: string;
  merchantRequestId: string;
  status: string;
}

export async function initiateStkPush(payload: StkPushPayload): Promise<StkPushResult> {
  const { data } = await api.post('/api/v1/payments/stk-push', payload);
  return data.data as StkPushResult;
}

export async function pollTransactionStatus(transactionId: string): Promise<Transaction> {
  const { data } = await api.get(`/api/v1/transactions/${transactionId}`);
  return data.data as Transaction;
}

/* ── Users ────────────────────────────────────────── */

export async function fetchUsers(): Promise<User[]> {
  const { data } = await api.get('/api/v1/users');
  return data.data as User[];
}

export async function createUser(payload: {
  username: string;
  email: string;
  password: string;
  role: string;
}): Promise<User> {
  const { data } = await api.post('/api/v1/users', payload);
  return data.data as User;
}

export async function deleteUser(userId: string): Promise<void> {
  await api.delete(`/api/v1/users/${userId}`);
}

export async function updateUserRole(userId: string, role: string): Promise<User> {
  const { data } = await api.patch(`/api/v1/users/${userId}`, { role });
  return data.data as User;
}

/* ── Audit ────────────────────────────────────────── */

export async function fetchAuditLogs(
  params: Record<string, string | number | undefined> = {}
): Promise<AuditLogEntry[]> {
  const { data } = await api.get('/api/v1/audit-logs', { params });
  return data.data?.content ?? data.data?.items ?? ([] as AuditLogEntry[]);
}

/* ── Settings & API Clients ────────────────────────── */

export interface PaybillConfig {
  shortcode: string;
  paybillNumber: string;
  environment: 'SANDBOX' | 'PRODUCTION';
  callbackUrlBase: string;
  passkeyConfigured: boolean;
}

export interface ApiClient {
  id: string;
  clientId: string;
  clientName: string;
  scopes: string[];
  status: 'ACTIVE' | 'REVOKED';
  createdAt: string;
}

export async function fetchSettings(): Promise<PaybillConfig> {
  try {
    const { data } = await api.get('/api/v1/settings/paybill');
    return data.data as PaybillConfig;
  } catch {
    return {
      shortcode: '174379',
      paybillNumber: '174379',
      environment: 'SANDBOX',
      callbackUrlBase: 'https://api.openfloat.co.ke',
      passkeyConfigured: true,
    };
  }
}

export async function updatePaybillConfig(config: Partial<PaybillConfig>): Promise<PaybillConfig> {
  const { data } = await api.put('/api/v1/settings/paybill', config);
  return data.data as PaybillConfig;
}

export async function fetchApiClients(): Promise<ApiClient[]> {
  try {
    const { data } = await api.get('/api/v1/settings/api-clients');
    return data.data as ApiClient[];
  } catch {
    return [
      {
        id: '1',
        clientId: 'openfloat-staff-portal',
        clientName: 'Staff Portal SPA',
        scopes: ['openid', 'profile', 'payments:write', 'transactions:read'],
        status: 'ACTIVE',
        createdAt: '2026-01-15T08:00:00Z',
      },
      {
        id: '2',
        clientId: 'erp-connector-service',
        clientName: 'Dynamics / SAP ERP Sync',
        scopes: ['events:read', 'transactions:read', 'reconciliation:write'],
        status: 'ACTIVE',
        createdAt: '2026-02-01T10:30:00Z',
      },
    ];
  }
}

export async function createApiClient(payload: { clientName: string; scopes: string[] }): Promise<ApiClient & { clientSecret?: string }> {
  const { data } = await api.post('/api/v1/settings/api-clients', payload);
  return data.data;
}

export async function revokeApiClient(clientId: string): Promise<void> {
  await api.post(`/api/v1/settings/api-clients/${clientId}/revoke`);
}

/* ── Dashboard Summary ────────────────────────────── */

export interface DashboardSummary {
  todayCount: number;
  todayVolume: number;
  pendingCount: number;
  failedCount: number;
  successRate: number;
}

export async function fetchDashboardSummary(): Promise<DashboardSummary> {
  try {
    const { data } = await api.get('/api/v1/dashboard/summary');
    return data.data as DashboardSummary;
  } catch {
    const txns = await fetchTransactions({ size: 200 });
    const today = new Date().toISOString().slice(0, 10);
    const todays = txns.filter((t) => t.createdAt?.startsWith(today));
    const volume = todays.reduce((sum, t) => sum + Number(t.amount || 0), 0);
    const pending = txns.filter((t) => t.status === 'PENDING').length;
    const failed = txns.filter((t) => t.status === 'FAILED').length;
    const completed = txns.filter((t) => t.status === 'SUCCESS').length;
    const successRate = txns.length > 0 ? (completed / txns.length) * 100 : 0;

    return {
      todayCount: todays.length,
      todayVolume: volume,
      pendingCount: pending,
      failedCount: failed,
      successRate: Math.round(successRate * 10) / 10,
    };
  }
}

/* ── Phase 8: Multi-Tenant & Client Applications ────── */

export interface ClientApp {
  id: string;
  clientName: string;
  accountPrefix: string;
  callbackUrl: string;
  status: 'ACTIVE' | 'SUSPENDED';
  registeredBy: string;
  notes?: string;
  createdAt: string;
  updatedAt?: string;
}

export interface ClientAppRegistrationResult extends ClientApp {
  apiKey: string;
  webhookSecret: string;
}

export async function fetchClientApps(): Promise<ClientApp[]> {
  try {
    const { data } = await api.get('/api/v1/clients');
    return data.data ?? data;
  } catch {
    return [
      {
        id: '1',
        clientName: 'XYZ School Portal',
        accountPrefix: 'SCH',
        callbackUrl: 'https://school.example.com/api/payment-webhook',
        status: 'ACTIVE',
        registeredBy: 'admin',
        createdAt: new Date().toISOString(),
      },
      {
        id: '2',
        clientName: 'Acme E-Commerce Store',
        accountPrefix: 'ECOMM',
        callbackUrl: 'https://acme.example.com/webhooks/mpesa',
        status: 'ACTIVE',
        registeredBy: 'manager',
        createdAt: new Date().toISOString(),
      },
    ];
  }
}

export async function registerClientApp(payload: {
  clientName: string;
  accountPrefix: string;
  callbackUrl: string;
  notes?: string;
}): Promise<ClientAppRegistrationResult> {
  const { data } = await api.post('/api/v1/clients', payload);
  return data.data ?? data;
}

export async function updateClientAppStatus(id: string, status: string): Promise<ClientApp> {
  const { data } = await api.put(`/api/v1/clients/${id}/status`, { status });
  return data.data ?? data;
}

export async function updateClientAppCallbackUrl(id: string, callbackUrl: string): Promise<ClientApp> {
  const { data } = await api.put(`/api/v1/clients/${id}/callback-url`, { callbackUrl });
  return data.data ?? data;
}

/* ── Phase 8: Dynamic Account References ─────────────── */

export interface AccountReferenceMappingDto {
  id: string;
  accountReference: string;
  clientAppId: string;
  clientAppName: string;
  accountPrefix: string;
  callbackUrl: string;
  requestedAmount?: number;
  currency: string;
  description?: string;
  status: 'PENDING' | 'PAID' | 'EXPIRED' | 'CANCELLED';
  expiresAt: string;
  paidAt?: string;
  transactionId?: string;
  createdAt: string;
}

export async function generateAccountReference(payload: {
  accountPrefix: string;
  requestedAmount?: number;
  description?: string;
  callbackUrlOverride?: string;
  ttlMinutes?: number;
}): Promise<AccountReferenceMappingDto> {
  const { data } = await api.post('/api/v1/references/generate', payload);
  return data.data ?? data;
}

export async function fetchClientAccountReferences(clientAppId: string): Promise<AccountReferenceMappingDto[]> {
  try {
    const { data } = await api.get(`/api/v1/references/client/${clientAppId}`);
    return data.data ?? data;
  } catch {
    return [];
  }
}

/* ── Phase 8: Webhook Monitoring & Redrive ───────────── */

export interface WebhookDeliveryLogDto {
  id: string;
  transactionId?: string;
  clientAppId: string;
  clientName: string;
  accountReference?: string;
  targetUrl: string;
  httpStatus?: number;
  requestPayload: String;
  responseBody?: string;
  errorMessage?: string;
  attemptNumber: number;
  success: boolean;
  deliveredAt?: string;
  createdAt: string;
}

export async function fetchFailedWebhooks(): Promise<WebhookDeliveryLogDto[]> {
  try {
    const { data } = await api.get('/api/v1/webhooks/failed');
    return data.data ?? data;
  } catch {
    return [];
  }
}

export async function redriveWebhook(logId: string): Promise<WebhookDeliveryLogDto> {
  const { data } = await api.post(`/api/v1/webhooks/${logId}/redrive`);
  return data.data ?? data;
}

/* ── Phase 8: Reconciliation Manual Override ─────────── */

export async function executeReconciliationOverride(payload: {
  accountReference: string;
  transactionId?: string;
  reason: string;
}): Promise<{ status: string; message: string }> {
  const { data } = await api.post('/api/v1/reconciliation/override', payload);
  return data.data ?? data;
}
