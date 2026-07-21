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
