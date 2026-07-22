import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { fetchTransaction } from '../api/queries';

interface Props {
  transactionId: string;
  onBack: () => void;
}

export default function TransactionDetailPage({ transactionId, onBack }: Props) {
  const { data: txn, isLoading, isError } = useQuery({
    queryKey: ['transaction-detail', transactionId],
    queryFn: () => fetchTransaction(transactionId),
  });

  if (isLoading) {
    return (
      <div className="loading-state">
        <span className="spinner" /> Loading transaction details…
      </div>
    );
  }

  if (isError || !txn) {
    return (
      <div className="card">
        <p style={{ color: 'var(--color-error)' }}>Failed to load transaction.</p>
        <button className="btn btn-ghost btn-sm" onClick={onBack}>← Back to list</button>
      </div>
    );
  }

  return (
    <>
      {/* ─── Back button ─── */}
      <button className="btn btn-ghost btn-sm" onClick={onBack} style={{ marginBottom: '1rem' }}>
        ← Back to Transactions
      </button>

      {/* ─── Header ─── */}
      <div className="card" style={{ marginBottom: '1rem' }}>
        <div className="toolbar">
          <div>
            <h3 style={{ margin: 0 }}>Transaction Detail</h3>
            <span style={{ fontSize: '0.82rem', color: 'var(--color-text-muted)', fontFamily: 'monospace' }}>
              {txn.id}
            </span>
          </div>
          <span className={`pill ${statusPillClass(txn.status)}`} style={{ fontSize: '0.9rem' }}>
            {txn.status}
          </span>
        </div>
      </div>

      {/* ─── Core Details ─── */}
      <div className="stats-grid" style={{ marginBottom: '1rem' }}>
        <div className="card stat-card">
          <span className="stat-label">Amount</span>
          <span className="stat-value">KES {Number(txn.amount).toLocaleString()}</span>
        </div>
        <div className="card stat-card">
          <span className="stat-label">MSISDN</span>
          <span className="stat-value" style={{ fontSize: '1.4rem', fontFamily: 'monospace' }}>
            {txn.msisdn}
          </span>
        </div>
        <div className="card stat-card">
          <span className="stat-label">Type</span>
          <span className="stat-value" style={{ fontSize: '1.2rem' }}>
            {txn.transactionType ?? '—'}
          </span>
        </div>
      </div>

      {/* ─── Extended Info Grid ─── */}
      <div className="two-col" style={{ marginBottom: '1rem' }}>
        {/* Left: Transaction Metadata */}
        <div className="card">
          <h3 style={{ marginTop: 0, marginBottom: '1rem', fontSize: '1rem' }}>Transaction Metadata</h3>
          <table>
            <tbody>
              <DetailRow label="Transaction ID" value={txn.transactionId} mono />
              <DetailRow label="M-Pesa Receipt" value={txn.mpesaReceiptNumber} mono />
              <DetailRow label="Conversation ID" value={txn.conversationId} mono />
              <DetailRow label="Account Reference" value={txn.accountReference} />
              <DetailRow label="Created" value={formatDateTime(txn.createdAt)} />
              <DetailRow label="Updated" value={formatDateTime(txn.updatedAt)} />
            </tbody>
          </table>
        </div>

        {/* Right: Reconciliation & ERP Sync */}
        <div className="card">
          <h3 style={{ marginTop: 0, marginBottom: '1rem', fontSize: '1rem' }}>Reconciliation & ERP Sync</h3>
          <table>
            <tbody>
              <tr>
                <td style={labelStyle}>Reconciliation</td>
                <td>
                  <span className={`pill ${reconPillClass(txn.reconciliationStatus)}`}>
                    {txn.reconciliationStatus ?? 'NOT_RECONCILED'}
                  </span>
                </td>
              </tr>
              <tr>
                <td style={labelStyle}>ERP Sync</td>
                <td>
                  <span className={`pill ${erpPillClass(txn.erpSyncStatus)}`}>
                    {txn.erpSyncStatus ?? 'NOT_APPLICABLE'}
                  </span>
                </td>
              </tr>
            </tbody>
          </table>

          <div style={{ marginTop: '1.5rem' }}>
            <h4 style={{ margin: '0 0 0.5rem', fontSize: '0.85rem', color: 'var(--color-text-muted)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
              Status Timeline
            </h4>
            <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
              <TimelineStep label="Created" active />
              <TimelineStep label="Callback" active={txn.status !== 'PENDING'} />
              <TimelineStep
                label="Reconciled"
                active={txn.reconciliationStatus === 'MATCHED' || txn.reconciliationStatus === 'MISMATCHED'}
              />
              <TimelineStep label="ERP Synced" active={txn.erpSyncStatus === 'SYNCED'} />
            </div>
          </div>
        </div>
      </div>

      {/* ─── Callback Payload ─── */}
      {txn.callbackPayload && (
        <div className="card">
          <h3 style={{ marginTop: 0, fontSize: '1rem' }}>Callback Payload</h3>
          <div className="result-block" style={{ marginTop: '0.75rem' }}>
            {JSON.stringify(txn.callbackPayload, null, 2)}
          </div>
        </div>
      )}
    </>
  );
}

/* ─── Sub-components ─── */
function DetailRow({ label, value, mono }: { label: string; value?: string; mono?: boolean }) {
  return (
    <tr>
      <td style={labelStyle}>{label}</td>
      <td style={mono ? { fontFamily: 'monospace', fontSize: '0.88rem' } : undefined}>
        {value ?? '—'}
      </td>
    </tr>
  );
}

function TimelineStep({ label, active }: { label: string; active: boolean }) {
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: '0.4rem',
        padding: '0.4rem 0.75rem',
        borderRadius: 'var(--radius-pill)',
        background: active ? 'rgba(0, 166, 80, 0.12)' : 'rgba(255, 255, 255, 0.04)',
        border: `1px solid ${active ? 'rgba(0, 166, 80, 0.25)' : 'var(--color-border)'}`,
        fontSize: '0.8rem',
        fontWeight: 600,
        color: active ? 'var(--color-brand-300)' : 'var(--color-text-muted)',
      }}
    >
      <span>{active ? '✓' : '○'}</span>
      {label}
    </div>
  );
}

/* ─── Helpers ─── */
const labelStyle: React.CSSProperties = {
  fontWeight: 600,
  color: 'var(--color-text-secondary)',
  width: '45%',
  fontSize: '0.88rem',
};

function statusPillClass(status: string): string {
  switch (status) {
    case 'SUCCESS': return 'pill-success';
    case 'PENDING': return 'pill-pending';
    case 'FAILED': return 'pill-failed';
    default: return 'pill-pending';
  }
}

function reconPillClass(status?: string): string {
  switch (status) {
    case 'MATCHED': return 'pill-success';
    case 'MISMATCHED': return 'pill-failed';
    case 'PENDING': return 'pill-pending';
    default: return '';
  }
}

function erpPillClass(status?: string): string {
  switch (status) {
    case 'SYNCED': return 'pill-success';
    case 'FAILED': return 'pill-failed';
    case 'PENDING': return 'pill-pending';
    default: return '';
  }
}

function formatDateTime(iso?: string): string {
  if (!iso) return '—';
  try {
    return new Date(iso).toLocaleString([], {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    });
  } catch {
    return iso;
  }
}
