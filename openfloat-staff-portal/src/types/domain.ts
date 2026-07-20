export type TransactionStatus='PENDING'|'SUCCESS'|'FAILED'|'REVERSAL_PENDING'|string;
export interface Transaction{ id:string; transactionId?:string; msisdn:string; amount:number; accountReference?:string; status:TransactionStatus; transactionType?:string; reconciliationStatus?:string; erpSyncStatus?:string; callbackPayload?:unknown; createdAt:string; }
export interface User{ id:string; username:string; email:string; role:'ADMIN'|'STAFF'|string; status:string; lastLogin?:string; createdAt:string; }
