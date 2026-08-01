import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  fetchClientApps,
  registerClientApp,
  updateClientAppStatus,
  updateClientAppCallbackUrl,
} from '../api/queries';
import type { ClientApp, ClientAppRegistrationResult } from '../api/queries';

export default function ClientManagementPage() {
  const queryClient = useQueryClient();
  const [showModal, setShowModal] = useState(false);
  const [newResult, setNewResult] = useState<ClientAppRegistrationResult | null>(null);

  const [form, setForm] = useState({
    clientName: '',
    accountPrefix: '',
    callbackUrl: '',
    notes: '',
  });

  const { data: clients = [], isLoading } = useQuery({
    queryKey: ['client-apps'],
    queryFn: fetchClientApps,
  });

  const registerMutation = useMutation({
    mutationFn: registerClientApp,
    onSuccess: (result) => {
      setNewResult(result);
      queryClient.invalidateQueries({ queryKey: ['client-apps'] });
      setForm({ clientName: '', accountPrefix: '', callbackUrl: '', notes: '' });
    },
  });

  const statusMutation = useMutation({
    mutationFn: ({ id, status }: { id: string; status: string }) => updateClientAppStatus(id, status),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['client-apps'] });
    },
  });

  const callbackUrlMutation = useMutation({
    mutationFn: ({ id, url }: { id: string; url: string }) => updateClientAppCallbackUrl(id, url),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['client-apps'] });
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    registerMutation.mutate(form);
  };

  return (
    <div className="client-management-page">
      <div className="toolbar" style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '1.5rem' }}>
        <div>
          <h3>Client Applications (Websites & Apps)</h3>
          <p className="text-muted">Register and manage external systems receiving M-Pesa webhooks</p>
        </div>
        <button className="btn btn-primary" onClick={() => { setShowModal(true); setNewResult(null); }}>
          Register New Client System
        </button>
      </div>

      {/* ─── Credentials Callout Banner ─── */}
      {newResult && (
        <div className="card result-block success" style={{ marginBottom: '1.5rem', borderLeft: '4px solid var(--color-brand-500)' }}>
          <h4 style={{ margin: '0 0 0.5rem 0' }}>Client Registration Successful!</h4>
          <p>Please copy these API credentials immediately. For security, raw keys are only shown once.</p>

          <div style={{ marginTop: '0.75rem', background: 'var(--color-surface-200)', padding: '0.75rem', borderRadius: '4px' }}>
            <div><strong>Client Name:</strong> {newResult.clientName}</div>
            <div><strong>Account Prefix:</strong> {newResult.accountPrefix}</div>
            <div><strong>Callback URL:</strong> {newResult.callbackUrl}</div>
            <div style={{ marginTop: '0.5rem', color: 'var(--color-brand-300)' }}>
              <strong>Issued API Key:</strong> <code>{newResult.apiKey}</code>
            </div>
            <div style={{ color: 'var(--color-brand-300)' }}>
              <strong>Webhook Secret (HMAC):</strong> <code>{newResult.webhookSecret}</code>
            </div>
          </div>
          <button className="btn btn-ghost btn-sm" style={{ marginTop: '0.75rem' }} onClick={() => setNewResult(null)}>
            Dismiss
          </button>
        </div>
      )}

      {/* ─── Clients Table ─── */}
      <div className="card">
        {isLoading ? (
          <p>Loading registered client systems...</p>
        ) : clients.length === 0 ? (
          <p className="text-muted">No client applications registered yet.</p>
        ) : (
          <table className="data-table">
            <thead>
              <tr>
                <th>System Name</th>
                <th>Prefix</th>
                <th>Registered Callback URL</th>
                <th>Status</th>
                <th>Registered By</th>
                <th>Created</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {clients.map((client) => (
                <tr key={client.id}>
                  <td><strong>{client.clientName}</strong></td>
                  <td><code>{client.accountPrefix}</code></td>
                  <td><small>{client.callbackUrl}</small></td>
                  <td>
                    <span className={`status-badge ${client.status.toLowerCase()}`}>
                      {client.status}
                    </span>
                  </td>
                  <td>{client.registeredBy}</td>
                  <td>{new Date(client.createdAt).toLocaleDateString()}</td>
                  <td>
                    <div style={{ display: 'flex', gap: '0.5rem' }}>
                      <button
                        className={`btn btn-sm ${client.status === 'ACTIVE' ? 'btn-secondary' : 'btn-primary'}`}
                        onClick={() => statusMutation.mutate({
                          id: client.id,
                          status: client.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE',
                        })}
                      >
                        {client.status === 'ACTIVE' ? 'Suspend' : 'Activate'}
                      </button>
                      <button
                        className="btn btn-ghost btn-sm"
                        onClick={() => {
                          const newUrl = prompt('Enter new Webhook Callback URL:', client.callbackUrl);
                          if (newUrl && newUrl !== client.callbackUrl) {
                            callbackUrlMutation.mutate({ id: client.id, url: newUrl });
                          }
                        }}
                      >
                        Edit URL
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {/* ─── Registration Modal ─── */}
      {showModal && (
        <div className="modal-overlay">
          <div className="card modal-card" style={{ maxWidth: 540 }}>
            <div className="toolbar" style={{ display: 'flex', justifyContent: 'space-between' }}>
              <h4>Register Client System</h4>
              <button className="btn btn-ghost btn-sm" onClick={() => setShowModal(false)}>Close</button>
            </div>

            <form onSubmit={handleSubmit} style={{ marginTop: '1rem' }}>
              <div className="form-group">
                <label>System / App Name</label>
                <input
                  type="text"
                  className="form-input"
                  placeholder="e.g. XYZ School Portal, Acme E-Commerce"
                  value={form.clientName}
                  onChange={(e) => setForm({ ...form, clientName: e.target.value })}
                  required
                />
              </div>

              <div className="form-group" style={{ marginTop: '0.75rem' }}>
                <label>Account Reference Prefix (Uppercase Alphanumeric)</label>
                <input
                  type="text"
                  className="form-input"
                  placeholder="e.g. SCH, ECOMM, POS"
                  value={form.accountPrefix}
                  onChange={(e) => setForm({ ...form, accountPrefix: e.target.value.toUpperCase() })}
                  required
                />
                <small className="text-muted">Unique prefix for references generated for this client (e.g. ECOMM-8X92K4)</small>
              </div>

              <div className="form-group" style={{ marginTop: '0.75rem' }}>
                <label>Webhook Callback URL</label>
                <input
                  type="url"
                  className="form-input"
                  placeholder="https://client.com/webhooks/mpesa"
                  value={form.callbackUrl}
                  onChange={(e) => setForm({ ...form, callbackUrl: e.target.value })}
                  required
                />
              </div>

              <div className="form-group" style={{ marginTop: '0.75rem' }}>
                <label>Notes / Description</label>
                <textarea
                  className="form-input"
                  rows={2}
                  placeholder="Optional internal notes"
                  value={form.notes}
                  onChange={(e) => setForm({ ...form, notes: e.target.value })}
                />
              </div>

              {registerMutation.isError && (
                <div className="result-block error" style={{ marginTop: '0.75rem' }}>
                  Error: {(registerMutation.error as Error)?.message ?? 'Registration failed'}
                </div>
              )}

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.5rem', marginTop: '1.25rem' }}>
                <button type="button" className="btn btn-secondary" onClick={() => setShowModal(false)}>Cancel</button>
                <button type="submit" className="btn btn-primary" disabled={registerMutation.isPending}>
                  {registerMutation.isPending ? 'Registering...' : 'Register Client System'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
