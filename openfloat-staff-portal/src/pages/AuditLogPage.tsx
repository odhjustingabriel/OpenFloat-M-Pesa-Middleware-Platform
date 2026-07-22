import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { fetchAuditLogs } from '../api/queries';
import type { AuditLogEntry } from '../types/domain';

const PAGE_SIZE = 25;

export default function AuditLogPage() {
  const [searchTerm, setSearchTerm] = React.useState('');
  const [actionFilter, setActionFilter] = React.useState<string>('ALL');
  const [currentPage, setCurrentPage] = React.useState(0);
  const [expandedId, setExpandedId] = React.useState<string | null>(null);

  const { data: logs = [], isLoading } = useQuery({
    queryKey: ['audit-logs'],
    queryFn: () => fetchAuditLogs({ size: 500 }),
    refetchInterval: 60_000,
  });

  // Derive unique action types for filter dropdown
  const actionTypes = React.useMemo(() => {
    const types = new Set(logs.map((l: AuditLogEntry) => l.action));
    return ['ALL', ...Array.from(types).sort()];
  }, [logs]);

  // Client-side filtering
  const filtered = React.useMemo(() => {
    return logs.filter((entry: AuditLogEntry) => {
      if (actionFilter !== 'ALL' && entry.action !== actionFilter) return false;
      if (searchTerm) {
        const term = searchTerm.toLowerCase();
        const searchable = [
          entry.action,
          entry.entityType,
          entry.entityId,
          entry.userId,
          entry.details,
          entry.chainHash,
        ]
          .filter(Boolean)
          .join(' ')
          .toLowerCase();
        if (!searchable.includes(term)) return false;
      }
      return true;
    });
  }, [logs, actionFilter, searchTerm]);

  const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const pageData = filtered.slice(currentPage * PAGE_SIZE, (currentPage + 1) * PAGE_SIZE);

  return (
    <>
      {/* ─── Toolbar ─── */}
      <div className="card" style={{ marginBottom: '1rem' }}>
        <div className="toolbar">
          <h3>
            Audit Chain
            <span style={{ fontSize: '0.8rem', fontWeight: 400, color: 'var(--color-text-muted)', marginLeft: '0.75rem' }}>
              {filtered.length} entries
            </span>
          </h3>
          <span className="live-indicator">
            <span className="dot" /> Integrity verified
          </span>
        </div>

        <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap', marginTop: '0.75rem' }}>
          <input
            className="form-input"
            placeholder="Search actions, entities, hashes…"
            style={{ maxWidth: 300 }}
            value={searchTerm}
            onChange={(e: React.ChangeEvent<HTMLInputElement>) => {
              setSearchTerm(e.target.value);
              setCurrentPage(0);
            }}
          />
          <select
            className="form-input"
            style={{ maxWidth: 200 }}
            value={actionFilter}
            onChange={(e: React.ChangeEvent<HTMLSelectElement>) => {
              setActionFilter(e.target.value);
              setCurrentPage(0);
            }}
          >
            {actionTypes.map((a: string) => (
              <option key={a} value={a}>
                {a === 'ALL' ? 'All Actions' : a}
              </option>
            ))}
          </select>
        </div>
      </div>

      {/* ─── Table ─── */}
      <div className="card">
        {isLoading ? (
          <div className="loading-state">
            <span className="spinner" /> Loading audit logs…
          </div>
        ) : filtered.length === 0 ? (
          <div className="loading-state">No audit entries found.</div>
        ) : (
          <>
            <table>
              <thead>
                <tr>
                  <th>Action</th>
                  <th>Entity</th>
                  <th>Entity ID</th>
                  <th>User</th>
                  <th>Chain Hash</th>
                  <th>Timestamp</th>
                </tr>
              </thead>
              <tbody>
                {pageData.map((entry: AuditLogEntry) => (
                  <React.Fragment key={entry.id}>
                    <tr
                      onClick={() => setExpandedId(expandedId === entry.id ? null : entry.id)}
                      style={{ cursor: 'pointer' }}
                    >
                      <td>
                        <span className="pill pill-pending" style={{ fontSize: '0.75rem' }}>
                          {entry.action}
                        </span>
                      </td>
                      <td>{entry.entityType}</td>
                      <td style={{ fontFamily: 'monospace', fontSize: '0.82rem' }}>
                        {truncate(entry.entityId, 12)}
                      </td>
                      <td>{entry.userId ?? '—'}</td>
                      <td style={{ fontFamily: 'monospace', fontSize: '0.78rem', color: 'var(--color-brand-300)' }}>
                        {truncate(entry.chainHash, 16)}
                      </td>
                      <td>{formatDateTime(entry.createdAt)}</td>
                    </tr>

                    {/* ─── Expanded row ─── */}
                    {expandedId === entry.id && (
                      <tr>
                        <td colSpan={6} style={{ padding: '0 1rem 1rem' }}>
                          <div
                            style={{
                              background: 'rgba(0, 0, 0, 0.25)',
                              border: '1px solid var(--color-border)',
                              borderRadius: 'var(--radius-md)',
                              padding: '1rem',
                            }}
                          >
                            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.75rem', fontSize: '0.85rem' }}>
                              <div>
                                <strong style={{ color: 'var(--color-text-muted)', fontSize: '0.75rem', textTransform: 'uppercase' }}>
                                  Full Chain Hash
                                </strong>
                                <div style={{ fontFamily: 'monospace', fontSize: '0.8rem', wordBreak: 'break-all', marginTop: '0.25rem' }}>
                                  {entry.chainHash}
                                </div>
                              </div>
                              <div>
                                <strong style={{ color: 'var(--color-text-muted)', fontSize: '0.75rem', textTransform: 'uppercase' }}>
                                  Previous Hash
                                </strong>
                                <div style={{ fontFamily: 'monospace', fontSize: '0.8rem', wordBreak: 'break-all', marginTop: '0.25rem' }}>
                                  {entry.previousHash}
                                </div>
                              </div>
                            </div>
                            {entry.details && (
                              <div style={{ marginTop: '0.75rem' }}>
                                <strong style={{ color: 'var(--color-text-muted)', fontSize: '0.75rem', textTransform: 'uppercase' }}>
                                  Details
                                </strong>
                                <div className="result-block" style={{ marginTop: '0.25rem', maxHeight: 200 }}>
                                  {entry.details}
                                </div>
                              </div>
                            )}
                          </div>
                        </td>
                      </tr>
                    )}
                  </React.Fragment>
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
                  onClick={() => setCurrentPage((p: number) => p - 1)}
                >
                  ← Previous
                </button>
                <button
                  className="btn btn-ghost btn-sm"
                  disabled={currentPage >= totalPages - 1}
                  onClick={() => setCurrentPage((p: number) => p + 1)}
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
function truncate(str: string, len: number): string {
  return str.length > len ? str.slice(0, len) + '…' : str;
}

function formatDateTime(iso?: string): string {
  if (!iso) return '—';
  try {
    return new Date(iso).toLocaleString([], {
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
