import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { fetchTransactions } from '../api/queries';
import type { Transaction } from '../types/domain';
import TransactionDetailPage from './TransactionDetailPage';

const PAGE_SIZE = 20;

const STATUS_OPTIONS = ['ALL', 'PENDING', 'SUCCESS', 'FAILED', 'REVERSAL_PENDING', 'REVERSED'] as const;
const TYPE_OPTIONS = ['ALL', 'STK_PUSH', 'B2C', 'C2B', 'REVERSAL'] as const;

export default function TransactionsPage() {
  const [currentPage, setCurrentPage] = React.useState(0);
  const [statusFilter, setStatusFilter] = React.useState<string>('ALL');
  const [typeFilter, setTypeFilter] = React.useState<string>('ALL');
  const [searchTerm, setSearchTerm] = React.useState('');
  const [selectedTxnId, setSelectedTxnId] = React.useState<string | null>(null);

  const { data: allTxns = [], isLoading } = useQuery({
    queryKey: ['transactions-all'],
    queryFn: () => fetchTransactions({ size: 500 }),
    refetchInterval: 30_000,
  });

  // Client-side filtering
  const filtered = React.useMemo(() => {
    return allTxns.filter((t: Transaction) => {
      if (statusFilter !== 'ALL' && t.status !== statusFilter) return false;
      if (typeFilter !== 'ALL' && t.transactionType !== typeFilter) return false;
      if (searchTerm) {
        const term = searchTerm.toLowerCase();
        const searchable = [
          t.msisdn,
          t.transactionId,
          t.mpesaReceiptNumber,
          t.accountReference,
          t.id,
        ]
          .filter(Boolean)
          .join(' ')
          .toLowerCase();
        if (!searchable.includes(term)) return false;
      }
      return true;
    });
  }, [allTxns, statusFilter, typeFilter, searchTerm]);

  // Pagination
  const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const pageData = filtered.slice(currentPage * PAGE_SIZE, (currentPage + 1) * PAGE_SIZE);

  // CSV export
  const exportCsv = () => {
    const header = 'ID,Transaction ID,MSISDN,Amount,Status,Type,Recon Status,ERP Sync,M-Pesa Receipt,Account Ref,Created At';
    const rows = filtered.map((t) =>
      [
        t.id,
        t.transactionId ?? '',
        t.msisdn,
        t.amount,
        t.status,
        t.transactionType ?? '',
        t.reconciliationStatus ?? '',
        t.erpSyncStatus ?? '',
        t.mpesaReceiptNumber ?? '',
        t.accountReference ?? '',
        t.createdAt,
      ].join(',')
    );
    const blob = new Blob([header + '\n' + rows.join('\n')], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `transactions-${new Date().toISOString().slice(0, 10)}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  };

  // If a transaction is selected, show its detail page
  if (selectedTxnId) {
    return (
      <TransactionDetailPage
        transactionId={selectedTxnId}
        onBack={() => setSelectedTxnId(null)}
      />
    );
  }

  return (
    <>
      {/* ─── Toolbar ─── */}
      <div className="card" style={{ marginBottom: '1rem' }}>
        <div className="toolbar">
          <h3>
            Transactions
            <span style={{ fontSize: '0.8rem', fontWeight: 400, color: 'var(--color-text-muted)', marginLeft: '0.75rem' }}>
              {filtered.length} results
            </span>
          </h3>
          <div className="toolbar-actions">
            <button className="btn btn-secondary btn-sm" onClick={exportCsv}>
              📥 Export CSV
            </button>
          </div>
        </div>

        {/* ─── Filters ─── */}
        <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap', marginTop: '0.75rem' }}>
          <input
            className="form-input"
            placeholder="Search MSISDN, receipt, ref…"
            style={{ maxWidth: 260 }}
            value={searchTerm}
            onChange={(e) => {
              setSearchTerm(e.target.value);
              setCurrentPage(0);
            }}
          />
          <select
            className="form-input"
            style={{ maxWidth: 160 }}
            value={statusFilter}
            onChange={(e) => {
              setStatusFilter(e.target.value);
              setCurrentPage(0);
            }}
          >
            {STATUS_OPTIONS.map((s) => (
              <option key={s} value={s}>
                {s === 'ALL' ? 'All Statuses' : s}
              </option>
            ))}
          </select>
          <select
            className="form-input"
            style={{ maxWidth: 160 }}
            value={typeFilter}
            onChange={(e) => {
              setTypeFilter(e.target.value);
              setCurrentPage(0);
            }}
          >
            {TYPE_OPTIONS.map((t) => (
              <option key={t} value={t}>
                {t === 'ALL' ? 'All Types' : t}
              </option>
            ))}
          </select>
        </div>
      </div>

      {/* ─── Table ─── */}
      <div className="card">
        {isLoading ? (
          <div className="loading-state">
            <span className="spinner" /> Loading transactions…
          </div>
        ) : filtered.length === 0 ? (
          <div className="loading-state">No transactions found matching your filters.</div>
        ) : (
          <>
            <table>
              <thead>
                <tr>
                  <th>MSISDN</th>
                  <th>Amount</th>
                  <th>Type</th>
                  <th>Status</th>
                  <th>Reconciliation</th>
                  <th>ERP Sync</th>
                  <th>Created</th>
                </tr>
              </thead>
              <tbody>
                {pageData.map((t) => (
                  <tr
                    key={t.id}
                    onClick={() => setSelectedTxnId(t.id)}
                    style={{ cursor: 'pointer' }}
                  >
                    <td style={{ fontFamily: 'monospace' }}>{t.msisdn}</td>
                    <td>KES {Number(t.amount).toLocaleString()}</td>
                    <td>{t.transactionType ?? '—'}</td>
                    <td>
                      <span className={`pill ${statusPillClass(t.status)}`}>{t.status}</span>
                    </td>
                    <td>
                      <span className={`pill ${reconPillClass(t.reconciliationStatus)}`}>
                        {t.reconciliationStatus ?? '—'}
                      </span>
                    </td>
                    <td>
                      <span className={`pill ${erpPillClass(t.erpSyncStatus)}`}>
                        {t.erpSyncStatus ?? '—'}
                      </span>
                    </td>
                    <td>{formatDate(t.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>

            {/* ─── Pagination ─── */}
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                marginTop: '1rem',
                padding: '0.5rem 0',
              }}
            >
              <span style={{ fontSize: '0.85rem', color: 'var(--color-text-muted)' }}>
                Page {currentPage + 1} of {totalPages}
              </span>
              <div className="toolbar-actions">
                <button
                  className="btn btn-ghost btn-sm"
                  disabled={currentPage === 0}
                  onClick={() => setCurrentPage((p) => p - 1)}
                >
                  ← Previous
                </button>
                <button
                  className="btn btn-ghost btn-sm"
                  disabled={currentPage >= totalPages - 1}
                  onClick={() => setCurrentPage((p) => p + 1)}
                >
                  Next →
                </button>
              </div>
            </div>
          </>
        )}
      </div>
    </>
  );
}

/* ─── Helpers ─── */
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

function formatDate(iso?: string): string {
  if (!iso) return '—';
  try {
    return new Date(iso).toLocaleString([], {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  } catch {
    return iso;
  }
}
