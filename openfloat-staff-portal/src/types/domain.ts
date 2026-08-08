export type TransactionStatus =
  | 'PENDING'
  | 'SUCCESS'
  | 'FAILED'
  | 'REVERSAL_PENDING'
  | 'REVERSED'
  | string;

export type ReconciliationStatus =
  | 'MATCHED'
  | 'MISMATCHED'
  | 'PENDING'
  | 'NOT_RECONCILED'
  | string;

export type ErpSyncStatus =
  | 'SYNCED'
  | 'PENDING'
  | 'FAILED'
  | 'NOT_APPLICABLE'
  | string;

export interface Transaction {
  id: string;
  transactionId?: string;
  msisdn: string;
  amount: number;
  accountReference?: string;
  status: TransactionStatus;
  transactionType?: string;
  reconciliationStatus?: ReconciliationStatus;
  erpSyncStatus?: ErpSyncStatus;
  callbackPayload?: unknown;
  mpesaReceiptNumber?: string;
  conversationId?: string;
  createdAt: string;
  updatedAt?: string;
}

export interface User {
  id: string;
  username: string;
  email: string;
  role: 'ADMIN' | 'STAFF' | string;
  status: string;
  lastLogin?: string;
  createdAt: string;
}

export interface AuditLogEntry {
  id: string;
  action: string;
  entityType: string;
  entityId: string;
  userId?: string;
  details?: string;
  chainHash: string;
  previousHash: string;
  createdAt: string;
}

/* ── Phase 9: Invoices ──────────────────────────────── */

export type InvoiceStatus = 'DRAFT' | 'ISSUED' | 'PAID' | 'CANCELLED' | string;

export interface Invoice {
  id: string;
  invoiceNumber: string;
  customerName: string;
  customerMsisdn: string;
  lineItems: InvoiceLineItem[];
  totalAmount: number;
  currency: string;
  status: InvoiceStatus;
  issuedAt?: string;
  dueDate?: string;
  paidAt?: string;
  transactionId?: string;
  notes?: string;
  createdAt: string;
  updatedAt?: string;
}

export interface InvoiceLineItem {
  description: string;
  quantity: number;
  unitPrice: number;
  subtotal: number;
}

export interface CreateInvoiceRequest {
  customerName: string;
  customerMsisdn: string;
  lineItems: Omit<InvoiceLineItem, 'subtotal'>[];
  dueDate?: string;
  notes?: string;
}

/* ── Phase 9: Bulk Payouts ──────────────────────────── */

export interface BulkPayoutItem {
  msisdn: string;
  amount: number;
  accountReference: string;
  remarks?: string;
}

export interface BulkPayoutResult {
  totalCount: number;
  successfulCount: number;
  failedCount: number;
  totalAmount: number;
  results: BulkPayoutItemResult[];
  processedAt: string;
}

export interface BulkPayoutItemResult {
  msisdn: string;
  amount: number;
  accountReference: string;
  success: boolean;
  transactionId?: string;
  errorMessage?: string;
}
