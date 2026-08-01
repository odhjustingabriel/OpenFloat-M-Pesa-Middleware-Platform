import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { fetchClientApps, generateAccountReference } from '../api/queries';
import type { AccountReferenceMappingDto } from '../api/queries';

export default function AccountReferencesPage() {
  const queryClient = useQueryClient();
  const [showModal, setShowModal] = useState(false);
  const [lastGenerated, setLastGenerated] = useState<AccountReferenceMappingDto | null>(null);

  const [form, setForm] = useState({
    accountPrefix: '',
    requestedAmount: '',
    description: '',
    callbackUrlOverride: '',
    ttlMinutes: 1440,
  });

  const { data: clients = [] } = useQuery({
    queryKey: ['client-apps'],
    queryFn: fetchClientApps,
  });

  const generateMutation = useMutation({
    mutationFn: (payload: {
      accountPrefix: string;
      requestedAmount?: number;
      description?: string;
      callbackUrlOverride?: string;
      ttlMinutes?: number;
    }) => generateAccountReference(payload),
    onSuccess: (result) => {
      setLastGenerated(result);
      queryClient.invalidateQueries({ queryKey: ['account-references'] });
      setForm({ accountPrefix: '', requestedAmount: '', description: '', callbackUrlOverride: '', ttlMinutes: 1440 });
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    generateMutation.mutate({
      accountPrefix: form.accountPrefix,
      requestedAmount: form.requestedAmount ? parseFloat(form.requestedAmount) : undefined,
      description: form.description || undefined,
      callbackUrlOverride: form.callbackUrlOverride || undefined,
      ttlMinutes: Number(form.ttlMinutes),
    });
  };

  return (
    <div className="account-references-page">
      <div className="toolbar" style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '1.5rem' }}>
        <div>
          <h3>Dynamic Account References</h3>
          <p className="text-muted">Generate & track unique reference codes (ECOMM-8X92K4) for client payments</p>
        </div>
        <button className="btn btn-primary" onClick={() => { setShowModal(true); setLastGenerated(null); }}>
          Generate Account Reference
        </button>
      </div>

      {/* ─── Last Generated Callout ─── */}
      {lastGenerated && (
        <div className="card result-block success" style={{ marginBottom: '1.5rem', borderLeft: '4px solid var(--color-brand-500)' }}>
          <h4 style={{ margin: '0 0 0.5rem 0' }}>Account Reference Generated</h4>
          <div style={{ background: 'var(--color-surface-200)', padding: '0.75rem', borderRadius: '4px', marginTop: '0.5rem' }}>
            <div style={{ fontSize: '1.2rem', color: 'var(--color-brand-300)' }}>
              <strong>Reference:</strong> <code>{lastGenerated.accountReference}</code>
            </div>
            <div><strong>Client App:</strong> {lastGenerated.clientAppName}</div>
            <div><strong>Target Callback:</strong> {lastGenerated.callbackUrl}</div>
            {lastGenerated.requestedAmount && (
              <div><strong>Expected Amount:</strong> KES {lastGenerated.requestedAmount.toLocaleString()}</div>
            )}
            <div><strong>Expires At:</strong> {new Date(lastGenerated.expiresAt).toLocaleString()}</div>
          </div>
          <button className="btn btn-ghost btn-sm" style={{ marginTop: '0.75rem' }} onClick={() => setLastGenerated(null)}>
            Dismiss
          </button>
        </div>
      )}

      {/* ─── Generation Modal ─── */}
      {showModal && (
        <div className="modal-overlay">
          <div className="card modal-card" style={{ maxWidth: 540 }}>
            <div className="toolbar" style={{ display: 'flex', justifyContent: 'space-between' }}>
              <h4>Generate Reference Code</h4>
              <button className="btn btn-ghost btn-sm" onClick={() => setShowModal(false)}>Close</button>
            </div>

            <form onSubmit={handleSubmit} style={{ marginTop: '1rem' }}>
              <div className="form-group">
                <label>Select Target Client Application</label>
                <select
                  className="form-input"
                  value={form.accountPrefix}
                  onChange={(e) => setForm({ ...form, accountPrefix: e.target.value })}
                  required
                >
                  <option value="">-- Choose Client App --</option>
                  {clients.map((c) => (
                    <option key={c.id} value={c.accountPrefix}>
                      {c.clientName} ({c.accountPrefix})
                    </option>
                  ))}
                </select>
              </div>

              <div className="form-group" style={{ marginTop: '0.75rem' }}>
                <label>Expected Payment Amount (Optional)</label>
                <input
                  type="number"
                  step="0.01"
                  className="form-input"
                  placeholder="e.g. 2500"
                  value={form.requestedAmount}
                  onChange={(e) => setForm({ ...form, requestedAmount: e.target.value })}
                />
              </div>

              <div className="form-group" style={{ marginTop: '0.75rem' }}>
                <label>Order / Invoice Description (Optional)</label>
                <input
                  type="text"
                  className="form-input"
                  placeholder="e.g. Invoice #1042"
                  value={form.description}
                  onChange={(e) => setForm({ ...form, description: e.target.value })}
                />
              </div>

              <div className="form-group" style={{ marginTop: '0.75rem' }}>
                <label>Time-To-Live (Minutes)</label>
                <input
                  type="number"
                  className="form-input"
                  value={form.ttlMinutes}
                  onChange={(e) => setForm({ ...form, ttlMinutes: Number(e.target.value) })}
                />
                <small className="text-muted">Default: 1440 minutes (24 hours)</small>
              </div>

              {generateMutation.isError && (
                <div className="result-block error" style={{ marginTop: '0.75rem' }}>
                  Error: {(generateMutation.error as Error)?.message ?? 'Generation failed'}
                </div>
              )}

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.5rem', marginTop: '1.25rem' }}>
                <button type="button" className="btn btn-secondary" onClick={() => setShowModal(false)}>Cancel</button>
                <button type="submit" className="btn btn-primary" disabled={generateMutation.isPending}>
                  {generateMutation.isPending ? 'Generating...' : 'Generate Reference Code'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
