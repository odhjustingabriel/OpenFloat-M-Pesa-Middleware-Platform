import React, { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { executeReconciliationOverride } from '../api/queries';

export default function ReconciliationPage() {
  const queryClient = useQueryClient();
  const [showOverrideModal, setShowOverrideModal] = useState(false);
  const [accountReference, setAccountReference] = useState('');
  const [reason, setReason] = useState('');
  const [resultMessage, setResultMessage] = useState<string | null>(null);

  const overrideMutation = useMutation({
    mutationFn: executeReconciliationOverride,
    onSuccess: (data) => {
      setResultMessage(data.message);
      setShowOverrideModal(false);
      setAccountReference('');
      setReason('');
      queryClient.invalidateQueries({ queryKey: ['transactions'] });
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    overrideMutation.mutate({ accountReference, reason });
  };

  return (
    <div className="reconciliation-page">
      <div className="toolbar" style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '1.5rem' }}>
        <div>
          <h3>Payment Reconciliation Hub</h3>
          <p className="text-muted">Cross-reference Paybill transactions against client Account References and execute Manager overrides</p>
        </div>
        <button className="btn btn-primary" onClick={() => { setShowOverrideModal(true); setResultMessage(null); }}>
          Execute Manual Override
        </button>
      </div>

      {/* ─── Metric Summary Cards ─── */}
      <div className="stats-grid" style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '1rem', marginBottom: '1.5rem' }}>
        <div className="card stat-card">
          <span className="stat-label">Automated Matched</span>
          <span className="stat-value">100%</span>
          <span className="stat-change positive">Nightly 02:00 UTC Cron</span>
        </div>
        <div className="card stat-card">
          <span className="stat-label">Unmatched Payments</span>
          <span className="stat-value">0</span>
          <span className="stat-change positive">All references mapped</span>
        </div>
        <div className="card stat-card">
          <span className="stat-label">Pending References</span>
          <span className="stat-value">Active</span>
          <span className="stat-change neutral">24h Expiration TTL</span>
        </div>
        <div className="card stat-card">
          <span className="stat-label">Discrepancies</span>
          <span className="stat-value">0</span>
          <span className="stat-change positive">Clean Ledger</span>
        </div>
      </div>

      {resultMessage && (
        <div className="card result-block success" style={{ marginBottom: '1.5rem' }}>
          <strong>Override Successful:</strong> {resultMessage}
        </div>
      )}

      {/* ─── Override Modal ─── */}
      {showOverrideModal && (
        <div className="modal-overlay">
          <div className="card modal-card" style={{ maxWidth: 500 }}>
            <div className="toolbar" style={{ display: 'flex', justifyContent: 'space-between' }}>
              <h4>Manager Reconciliation Override</h4>
              <button className="btn btn-ghost btn-sm" onClick={() => setShowOverrideModal(false)}>Close</button>
            </div>

            <form onSubmit={handleSubmit} style={{ marginTop: '1rem' }}>
              <div className="form-group">
                <label>Target Account Reference Code</label>
                <input
                  type="text"
                  className="form-input"
                  placeholder="e.g. ECOMM-8X92K4"
                  value={accountReference}
                  onChange={(e) => setAccountReference(e.target.value.toUpperCase())}
                  required
                />
              </div>

              <div className="form-group" style={{ marginTop: '0.75rem' }}>
                <label>Manager Reconciliation Reason</label>
                <textarea
                  className="form-input"
                  rows={3}
                  placeholder="State reason for manual payment override"
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                  required
                />
              </div>

              {overrideMutation.isError && (
                <div className="result-block error" style={{ marginTop: '0.75rem' }}>
                  Error: {(overrideMutation.error as Error)?.message ?? 'Override failed'}
                </div>
              )}

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.5rem', marginTop: '1.25rem' }}>
                <button type="button" className="btn btn-secondary" onClick={() => setShowOverrideModal(false)}>Cancel</button>
                <button type="submit" className="btn btn-primary" disabled={overrideMutation.isPending}>
                  {overrideMutation.isPending ? 'Executing...' : 'Confirm Reconciliation Override'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
