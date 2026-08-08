import { api } from './client';
import type { Transaction, User, AuditLogEntry, Invoice, CreateInvoiceRequest, BulkPayoutResult } from '../types/domain';

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

const DEFAULT_DEMO_USERS: User[] = [
  { id: '1', username: 'admin', email: 'admin@openfloat.com', role: 'ADMIN', status: 'ACTIVE', lastLogin: new Date().toISOString(), createdAt: new Date().toISOString() },
  { id: '2', username: 'manager', email: 'manager@openfloat.com', role: 'MANAGER', status: 'ACTIVE', lastLogin: new Date().toISOString(), createdAt: new Date().toISOString() },
  { id: '3', username: 'operator', email: 'operator@openfloat.com', role: 'OPERATOR', status: 'ACTIVE', lastLogin: new Date().toISOString(), createdAt: new Date().toISOString() },
  { id: '4', username: 'finance', email: 'finance@openfloat.com', role: 'FINANCE', status: 'ACTIVE', lastLogin: new Date().toISOString(), createdAt: new Date().toISOString() },
];

function getDemoUsers(): User[] {
  const stored = localStorage.getItem('openfloat.demo_users');
  if (stored) {
    try { return JSON.parse(stored); } catch {}
  }
  localStorage.setItem('openfloat.demo_users', JSON.stringify(DEFAULT_DEMO_USERS));
  return DEFAULT_DEMO_USERS;
}

function saveDemoUsers(users: User[]): void {
  localStorage.setItem('openfloat.demo_users', JSON.stringify(users));
}

export async function fetchUsers(): Promise<User[]> {
  try {
    const { data } = await api.get('/api/v1/users');
    const res = data.data ?? data;
    const list = Array.isArray(res) ? res : (Array.isArray(res?.content) ? res.content : []);
    return list.length > 0 ? list : getDemoUsers();
  } catch {
    return getDemoUsers();
  }
}

export async function createUser(payload: {
  username: string;
  email: string;
  password: string;
  role: string;
}): Promise<User> {
  try {
    const { data } = await api.post('/api/v1/users', payload);
    return (data.data ?? data) as User;
  } catch {
    const users = getDemoUsers();
    const newUser: User = {
      id: crypto.randomUUID(),
      username: payload.username,
      email: payload.email,
      role: payload.role as any,
      status: 'ACTIVE',
      createdAt: new Date().toISOString(),
    };
    users.push(newUser);
    saveDemoUsers(users);
    return newUser;
  }
}

export async function deleteUser(userId: string): Promise<void> {
  try {
    await api.delete(`/api/v1/users/${userId}`);
  } catch {
    const users = getDemoUsers().filter((u) => u.id !== userId);
    saveDemoUsers(users);
  }
}

export async function updateUserRole(userId: string, role: string): Promise<User> {
  try {
    const { data } = await api.patch(`/api/v1/users/${userId}`, { role });
    return (data.data ?? data) as User;
  } catch {
    const users = getDemoUsers();
    const target = users.find((u) => u.id === userId);
    if (target) {
      target.role = role as any;
      saveDemoUsers(users);
      return target;
    }
    throw new Error('User not found');
  }
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
    const res = data.data ?? data;
    const list = Array.isArray(res) ? res : (Array.isArray(res?.content) ? res.content : []);
    return list.length > 0 ? list : getDemoClientApps();
  } catch {
    return getDemoClientApps();
  }
}

function getDemoClientApps(): ClientApp[] {
  return [
    {
      id: '1',
      clientName: 'XYZ School Portal',
      accountPrefix: 'SCH',
      callbackUrl: 'https://school.example.com/api/payment-webhook',
      status: 'ACTIVE',
      registeredBy: 'admin',
      notes: 'Main student fee payment integration',
      createdAt: new Date(Date.now() - 86400000 * 5).toISOString(),
    },
    {
      id: '2',
      clientName: 'Acme E-Commerce Store',
      accountPrefix: 'ECOMM',
      callbackUrl: 'https://acme.example.com/webhooks/mpesa',
      status: 'ACTIVE',
      registeredBy: 'manager',
      notes: 'Online checkout payment gateway',
      createdAt: new Date(Date.now() - 86400000 * 2).toISOString(),
    },
  ];
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
    const res = data.data ?? data;
    return Array.isArray(res) ? res : (Array.isArray(res?.content) ? res.content : []);
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
  requestPayload: string;
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
    const res = data.data ?? data;
    const list = Array.isArray(res) ? res : (Array.isArray(res?.content) ? res.content : []);
    return list.length > 0 ? list : getDemoWebhookLogs();
  } catch {
    return getDemoWebhookLogs();
  }
}

function getDemoWebhookLogs(): WebhookDeliveryLogDto[] {
  return [
    {
      id: 'wh-101',
      clientAppId: '2',
      clientName: 'Acme E-Commerce Store',
      accountReference: 'ECOMM-8X92K4',
      targetUrl: 'https://acme.example.com/webhooks/mpesa',
      httpStatus: 504,
      requestPayload: JSON.stringify({ event: 'payment.success', accountReference: 'ECOMM-8X92K4', amount: 2500, status: 'SUCCESS' }),
      responseBody: 'Gateway Timeout from target server',
      errorMessage: 'HTTP 504 Gateway Timeout',
      attemptNumber: 1,
      success: false,
      createdAt: new Date(Date.now() - 3600000 * 3).toISOString(),
    },
    {
      id: 'wh-102',
      clientAppId: '1',
      clientName: 'XYZ School Portal',
      accountReference: 'SCH-4Y71P9',
      targetUrl: 'https://school.example.com/api/payment-webhook',
      httpStatus: 500,
      requestPayload: JSON.stringify({ event: 'payment.success', accountReference: 'SCH-4Y71P9', amount: 15000, status: 'SUCCESS' }),
      responseBody: 'Internal Server Error: Database Connection Pool Exhausted',
      errorMessage: 'HTTP 500 Internal Server Error',
      attemptNumber: 2,
      success: false,
      createdAt: new Date(Date.now() - 3600000 * 1).toISOString(),
    },
  ];
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

/* ── Phase 9: Invoices ────────────────────────────────── */

export async function fetchInvoices(
  params: Record<string, string | number | undefined> = {}
): Promise<Invoice[]> {
  try {
    const { data } = await api.get('/api/v1/invoices', { params });
    const res = data.data ?? data;
    const list = Array.isArray(res) ? res : (Array.isArray(res?.content) ? res.content : []);
    return list.length > 0 ? list : getDemoInvoices();
  } catch {
    return getDemoInvoices();
  }
}

function getDemoInvoices(): Invoice[] {
  return [
    {
      id: 'inv-001',
      invoiceNumber: 'INV-2026-0001',
      customerName: 'Kamau Enterprises Ltd',
      customerMsisdn: '254712345678',
      lineItems: [
        { description: 'Platform Subscription – July 2026', quantity: 1, unitPrice: 25000, subtotal: 25000 },
        { description: 'SMS Notification Bundle (500 units)', quantity: 1, unitPrice: 5000, subtotal: 5000 },
      ],
      totalAmount: 30000,
      currency: 'KES',
      status: 'ISSUED',
      issuedAt: new Date(Date.now() - 86400000 * 3).toISOString(),
      dueDate: new Date(Date.now() + 86400000 * 27).toISOString(),
      createdAt: new Date(Date.now() - 86400000 * 3).toISOString(),
    },
    {
      id: 'inv-002',
      invoiceNumber: 'INV-2026-0002',
      customerName: 'Safiri Travel Agency',
      customerMsisdn: '254723456789',
      lineItems: [
        { description: 'M-Pesa Integration Fee – Q3 2026', quantity: 1, unitPrice: 15000, subtotal: 15000 },
      ],
      totalAmount: 15000,
      currency: 'KES',
      status: 'PAID',
      issuedAt: new Date(Date.now() - 86400000 * 10).toISOString(),
      dueDate: new Date(Date.now() - 86400000 * 3).toISOString(),
      paidAt: new Date(Date.now() - 86400000 * 4).toISOString(),
      transactionId: 'TXN-88271',
      createdAt: new Date(Date.now() - 86400000 * 10).toISOString(),
    },
    {
      id: 'inv-003',
      invoiceNumber: 'INV-2026-0003',
      customerName: 'Uwezo Microfinance',
      customerMsisdn: '254734567890',
      lineItems: [
        { description: 'Bulk B2C Disbursement Service – July', quantity: 1, unitPrice: 8000, subtotal: 8000 },
        { description: 'ERP Connector License', quantity: 1, unitPrice: 12000, subtotal: 12000 },
      ],
      totalAmount: 20000,
      currency: 'KES',
      status: 'DRAFT',
      createdAt: new Date(Date.now() - 86400000 * 1).toISOString(),
    },
  ];
}

export async function createInvoice(payload: CreateInvoiceRequest): Promise<Invoice> {
  try {
    const { data } = await api.post('/api/v1/invoices', payload);
    return data.data ?? data;
  } catch {
    // Demo fallback
    const items = payload.lineItems.map((li) => ({
      ...li,
      subtotal: li.quantity * li.unitPrice,
    }));
    const total = items.reduce((s, li) => s + li.subtotal, 0);
    const inv: Invoice = {
      id: crypto.randomUUID(),
      invoiceNumber: `INV-2026-${String(Math.floor(Math.random() * 9000) + 1000)}`,
      customerName: payload.customerName,
      customerMsisdn: payload.customerMsisdn,
      lineItems: items,
      totalAmount: total,
      currency: 'KES',
      status: 'DRAFT',
      dueDate: payload.dueDate,
      notes: payload.notes,
      createdAt: new Date().toISOString(),
    };
    return inv;
  }
}

export async function cancelInvoice(invoiceId: string): Promise<Invoice> {
  try {
    const { data } = await api.post(`/api/v1/invoices/${invoiceId}/cancel`);
    return data.data ?? data;
  } catch {
    throw new Error('Failed to cancel invoice');
  }
}

export async function issueInvoice(invoiceId: string): Promise<Invoice> {
  try {
    const { data } = await api.post(`/api/v1/invoices/${invoiceId}/issue`);
    return data.data ?? data;
  } catch {
    throw new Error('Failed to issue invoice');
  }
}

/* ── Phase 9: Bulk Payouts ────────────────────────────── */

export async function submitBulkPayout(
  items: Array<{ msisdn: string; amount: number; accountReference: string; remarks?: string }>
): Promise<BulkPayoutResult> {
  const { data } = await api.post('/api/v1/payments/b2c/bulk', { items });
  return data.data ?? data;
}

export async function uploadBulkPayoutCsv(file: File): Promise<BulkPayoutResult> {
  const formData = new FormData();
  formData.append('file', file);
  const { data } = await api.post('/api/v1/payments/b2c/bulk/csv', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return data.data ?? data;
}

/* ── Phase 9: Statement Export (PDF / Excel) ──────────── */

/**
 * Triggers a browser download of a PDF statement from the backend.
 * Falls back gracefully when the backend is unavailable (dev mode).
 */
export async function downloadTransactionsPdf(
  params: Record<string, string | number | undefined> = {}
): Promise<void> {
  try {
    const response = await api.get('/api/v1/reports/transactions/pdf', {
      params,
      responseType: 'blob',
    });
    const blob = new Blob([response.data as BlobPart], { type: 'application/pdf' });
    triggerDownload(blob, `transactions-${today()}.pdf`);
  } catch {
    alert('PDF export is not yet connected to the backend. Configure /api/v1/reports/transactions/pdf.');
  }
}

/**
 * Triggers a browser download of an Excel statement from the backend.
 */
export async function downloadTransactionsExcel(
  params: Record<string, string | number | undefined> = {}
): Promise<void> {
  try {
    const response = await api.get('/api/v1/reports/transactions/excel', {
      params,
      responseType: 'blob',
    });
    const blob = new Blob([response.data as BlobPart], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    });
    triggerDownload(blob, `transactions-${today()}.xlsx`);
  } catch {
    alert('Excel export is not yet connected to the backend. Configure /api/v1/reports/transactions/excel.');
  }
}

function triggerDownload(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}

function today(): string {
  return new Date().toISOString().slice(0, 10);
}
