import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { LineChart, Line, BarChart, Bar, ResponsiveContainer, Tooltip, XAxis, YAxis, CartesianGrid } from 'recharts';
import { fetchDashboardSummary, fetchTransactions } from '../api/queries';
import type { Transaction } from '../types/domain';

export default function DashboardPage() {
  const summary = useQuery({
    queryKey: ['dashboard-summary'],
    queryFn: fetchDashboardSummary,
    refetchInterval: 30_000,
  });

  const recentTxns = useQuery({
    queryKey: ['recent-txns'],
    queryFn: () => fetchTransactions({ size: 50 }),
    refetchInterval: 30_000,
  });

  const stats = summary.data;
  const txns: Transaction[] = recentTxns.data ?? [];

  // Aggregate hourly volumes for chart
  const hourlyData = React.useMemo(() => {
    const buckets = new Map<string, { hour: string; volume: number; count: number }>();
    for (const t of txns) {
      const hourKey = t.createdAt?.slice(0, 13) ?? 'unknown';
      const h = hourKey.slice(11, 13) + ':00';
      const bucket = buckets.get(h) ?? { hour: h, volume: 0, count: 0 };
      bucket.volume += Number(t.amount || 0);
      bucket.count += 1;
      buckets.set(h, bucket);
    }
    return Array.from(buckets.values()).slice(-12);
  }, [txns]);

  // Status distribution for bar chart
  const statusDist = React.useMemo(() => {
    const counts: Record<string, number> = {};
    for (const t of txns) {
      const s = t.status || 'UNKNOWN';
      counts[s] = (counts[s] || 0) + 1;
    }
    return Object.entries(counts).map(([status, count]) => ({ status, count }));
  }, [txns]);

  return (
    <>
      {/* ─── Summary Cards ─── */}
      <div className="stats-grid">
        <div className="card stat-card">
          <span className="stat-label">Today's Transactions</span>
          <span className="stat-value">{stats?.todayCount ?? '—'}</span>
        </div>

        <div className="card stat-card">
          <span className="stat-label">Today's Volume</span>
          <span className="stat-value">
            KES {(stats?.todayVolume ?? 0).toLocaleString()}
          </span>
        </div>

        <div className="card stat-card">
          <span className="stat-label">Pending</span>
          <span className="stat-value">{stats?.pendingCount ?? '—'}</span>
          <span className="stat-change" style={{ color: 'var(--color-warning)' }}>
            Awaiting callback
          </span>
        </div>

        <div className="card stat-card">
          <span className="stat-label">Success Rate</span>
          <span className="stat-value">{stats?.successRate ?? '—'}%</span>
          <span className={`stat-change ${(stats?.successRate ?? 0) >= 90 ? 'positive' : 'negative'}`}>
            {(stats?.successRate ?? 0) >= 90 ? '✓ Healthy' : '⚠ Below threshold'}
          </span>
        </div>
      </div>

      {/* ─── Charts ─── */}
      <div className="two-col">
        <div className="card chart-container">
          <h3>Hourly Volume (KES)</h3>
          <ResponsiveContainer width="100%" height={240}>
            <LineChart data={hourlyData}>
              <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.06)" />
              <XAxis dataKey="hour" stroke="#5c7a6b" fontSize={12} />
              <YAxis stroke="#5c7a6b" fontSize={12} />
              <Tooltip
                contentStyle={{
                  background: '#0e1f18',
                  border: '1px solid rgba(255,255,255,0.12)',
                  borderRadius: 12,
                  color: '#eefbf4',
                }}
              />
              <Line
                type="monotone"
                dataKey="volume"
                stroke="#00A650"
                strokeWidth={3}
                dot={{ r: 4, fill: '#00A650' }}
                activeDot={{ r: 6, fill: '#2ebd6a' }}
              />
            </LineChart>
          </ResponsiveContainer>
        </div>

        <div className="card chart-container">
          <h3>Status Distribution</h3>
          <ResponsiveContainer width="100%" height={240}>
            <BarChart data={statusDist}>
              <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.06)" />
              <XAxis dataKey="status" stroke="#5c7a6b" fontSize={11} />
              <YAxis stroke="#5c7a6b" fontSize={12} />
              <Tooltip
                contentStyle={{
                  background: '#0e1f18',
                  border: '1px solid rgba(255,255,255,0.12)',
                  borderRadius: 12,
                  color: '#eefbf4',
                }}
              />
              <Bar dataKey="count" fill="#00A650" radius={[6, 6, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      </div>

      {/* ─── Recent Transactions ─── */}
      <div className="card">
        <div className="toolbar">
          <h3>Recent Transactions</h3>
          <span className="live-indicator">
            <span className="dot" /> Live
          </span>
        </div>
        {recentTxns.isLoading ? (
          <div className="loading-state">
            <span className="spinner" /> Loading transactions…
          </div>
        ) : (
          <table>
            <thead>
              <tr>
                <th>MSISDN</th>
                <th>Amount</th>
                <th>Type</th>
                <th>Status</th>
                <th>Time</th>
              </tr>
            </thead>
            <tbody>
              {txns.slice(0, 10).map((t) => (
                <tr key={t.id}>
                  <td>{t.msisdn}</td>
                  <td>KES {Number(t.amount).toLocaleString()}</td>
                  <td>{t.transactionType ?? '—'}</td>
                  <td>
                    <span className={`pill ${statusPillClass(t.status)}`}>
                      {t.status}
                    </span>
                  </td>
                  <td>{formatTime(t.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </>
  );
}

function statusPillClass(status: string): string {
  switch (status) {
    case 'SUCCESS': return 'pill-success';
    case 'PENDING': return 'pill-pending';
    case 'FAILED': return 'pill-failed';
    default: return 'pill-pending';
  }
}

function formatTime(iso?: string): string {
  if (!iso) return '—';
  try {
    return new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  } catch {
    return iso;
  }
}
