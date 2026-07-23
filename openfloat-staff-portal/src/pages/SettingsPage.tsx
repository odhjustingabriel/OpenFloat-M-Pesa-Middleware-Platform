import React from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  fetchSettings,
  updatePaybillConfig,
  fetchApiClients,
  createApiClient,
  revokeApiClient,
  PaybillConfig,
  ApiClient,
} from '../api/queries';

export default function SettingsPage() {
  const queryClient = useQueryClient();
  const [showClientModal, setShowClientModal] = React.useState(false);
  const [newClientName, setNewClientName] = React.useState('');
  const [selectedScopes, setSelectedScopes] = React.useState<string[]>([
    'openid',
    'transactions:read',
  ]);
  const [issuedSecret, setIssuedSecret] = React.useState<string | null>(null);

  const settingsQuery = useQuery({
    queryKey: ['settings-paybill'],
    queryFn: fetchSettings,
  });

  const clientsQuery = useQuery({
    queryKey: ['api-clients'],
    queryFn: fetchApiClients,
  });

  const paybillMutation = useMutation({
    mutationFn: updatePaybillConfig,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['settings-paybill'] });
    },
  });

  const createClientMutation = useMutation({
    mutationFn: createApiClient,
    onSuccess: (data: ApiClient & { clientSecret?: string }) => {
      queryClient.invalidateQueries({ queryKey: ['api-clients'] });
      if (data.clientSecret) {
        setIssuedSecret(data.clientSecret);
      } else {
        setShowClientModal(false);
        setNewClientName('');
      }
    },
  });

  const revokeClientMutation = useMutation({
    mutationFn: revokeApiClient,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['api-clients'] });
    },
  });

  const paybillConfig: PaybillConfig = settingsQuery.data ?? {
    shortcode: '174379',
    paybillNumber: '174379',
    environment: 'SANDBOX',
    callbackUrlBase: 'https://api.openfloat.co.ke',
    passkeyConfigured: true,
  };

  const apiClients: ApiClient[] = clientsQuery.data ?? [];

  const handleScopeToggle = (scope: string) => {
    setSelectedScopes((prev: string[]) =>
      prev.includes(scope) ? prev.filter((s: string) => s !== scope) : [...prev, scope]
    );
  };

  return (
    <>
      {/* ─── Grid: Paybill Config & Gateway IP Whitelist ─── */}
      <div className="two-col" style={{ marginBottom: '1.5rem' }}>
        {/* Left: Paybill Configuration */}
        <div className="card">
          <div className="toolbar">
            <h3 style={{ margin: 0 }}>M-Pesa Paybill Config</h3>
            <span
              className={`pill ${
                paybillConfig.environment === 'PRODUCTION' ? 'pill-failed' : 'pill-success'
              }`}
            >
              {paybillConfig.environment}
            </span>
          </div>

          <form
            onSubmit={(e: React.FormEvent) => {
              e.preventDefault();
              const form = e.target as HTMLFormElement;
              const shortcode = (form.elements.namedItem('shortcode') as HTMLInputElement).value;
              const callbackUrlBase = (
                form.elements.namedItem('callbackUrlBase') as HTMLInputElement
              ).value;
              paybillMutation.mutate({ shortcode, paybillNumber: shortcode, callbackUrlBase });
            }}
            style={{ marginTop: '1rem' }}
          >
            <div className="form-group">
              <label htmlFor="shortcode">Business Shortcode / Paybill</label>
              <input
                id="shortcode"
                name="shortcode"
                className="form-input"
                defaultValue={paybillConfig.shortcode}
              />
            </div>

            <div className="form-group">
              <label htmlFor="callbackUrlBase">Callback URL Base</label>
              <input
                id="callbackUrlBase"
                name="callbackUrlBase"
                className="form-input"
                defaultValue={paybillConfig.callbackUrlBase}
              />
            </div>

            <div className="form-group">
              <label>Lipa Na M-Pesa Passkey</label>
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '0.5rem',
                  fontSize: '0.9rem',
                  color: paybillConfig.passkeyConfigured ? 'var(--color-brand-300)' : 'var(--color-warning)',
                }}
              >
                <span>{paybillConfig.passkeyConfigured ? '✓ Encrypted in Vault' : '⚠ Missing Passkey'}</span>
              </div>
            </div>

            <button type="submit" className="btn btn-primary btn-sm" disabled={paybillMutation.isPending}>
              {paybillMutation.isPending ? <span className="spinner" /> : 'Save Paybill Settings'}
            </button>
          </form>
        </div>

        {/* Right: Gateway Callback Whitelist */}
        <div className="card">
          <div className="toolbar">
            <h3 style={{ margin: 0 }}>Gateway Callback IP Whitelist</h3>
            <span className="pill pill-success">ENFORCED</span>
          </div>
          <p style={{ fontSize: '0.85rem', color: 'var(--color-text-secondary)', marginTop: '0.5rem' }}>
            Safaricom Daraja webhook routes (<code>/api/v1/mpesa/callbacks/**</code>) are restricted
            to verified subnets at the API Gateway level.
          </p>

          <table style={{ marginTop: '1rem' }}>
            <thead>
              <tr>
                <th>Subnet Range</th>
                <th>Target</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td style={{ fontFamily: 'monospace' }}>196.201.214.0/24</td>
                <td>Safaricom G2 Production</td>
                <td><span className="pill pill-success">Allowed</span></td>
              </tr>
              <tr>
                <td style={{ fontFamily: 'monospace' }}>196.201.213.0/24</td>
                <td>Safaricom G2 Backup</td>
                <td><span className="pill pill-success">Allowed</span></td>
              </tr>
              <tr>
                <td style={{ fontFamily: 'monospace' }}>196.201.212.0/24</td>
                <td>Safaricom Sandbox</td>
                <td><span className="pill pill-success">Allowed</span></td>
              </tr>
              <tr>
                <td style={{ fontFamily: 'monospace' }}>127.0.0.1/32</td>
                <td>Localhost Testing</td>
                <td><span className="pill pill-pending">Dev Only</span></td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      {/* ─── API Clients Section ─── */}
      <div className="card">
        <div className="toolbar">
          <div>
            <h3 style={{ margin: 0 }}>OAuth API Clients</h3>
            <span style={{ fontSize: '0.82rem', color: 'var(--color-text-muted)' }}>
              Registered OAuth2 Client Credentials for external integrations & SPAs
            </span>
          </div>
          <button className="btn btn-primary btn-sm" onClick={() => setShowClientModal(true)}>
            + Issue API Client
          </button>
        </div>

        {/* ─── Modal / Form to Register Client ─── */}
        {showClientModal && (
          <div
            className="card"
            style={{
              marginTop: '1rem',
              marginBottom: '1rem',
              border: '1px solid var(--color-brand-400)',
              background: 'rgba(0,0,0,0.4)',
            }}
          >
            <div className="toolbar">
              <h4 style={{ margin: 0 }}>Issue New API Client</h4>
              <button
                className="btn btn-ghost btn-sm"
                onClick={() => {
                  setShowClientModal(false);
                  setIssuedSecret(null);
                }}
              >
                ✕ Close
              </button>
            </div>

            {issuedSecret ? (
              <div className="result-block success" style={{ marginTop: '1rem' }}>
                <strong>Client Credentials Issued Successfully!</strong>
                {'\n\n'}Please copy your Client Secret now. It will not be shown again:
                {'\n'}<code>{issuedSecret}</code>
              </div>
            ) : (
              <form
                onSubmit={(e: React.FormEvent) => {
                  e.preventDefault();
                  if (!newClientName) return;
                  createClientMutation.mutate({
                    clientName: newClientName,
                    scopes: selectedScopes,
                  });
                }}
                style={{ marginTop: '1rem' }}
              >
                <div className="form-group">
                  <label htmlFor="clientName">Client Application Name</label>
                  <input
                    id="clientName"
                    className="form-input"
                    placeholder="e.g. Finance Reporting Microservice"
                    value={newClientName}
                    onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
                      setNewClientName(e.target.value)
                    }
                  />
                </div>

                <div className="form-group">
                  <label>Authorized Scopes</label>
                  <div style={{ display: 'flex', gap: '1rem', flexWrap: 'wrap', marginTop: '0.25rem' }}>
                    {['openid', 'profile', 'payments:write', 'transactions:read', 'reconciliation:write'].map(
                      (scope: string) => (
                        <label
                          key={scope}
                          style={{
                            display: 'flex',
                            alignItems: 'center',
                            gap: '0.4rem',
                            cursor: 'pointer',
                            fontSize: '0.88rem',
                            textTransform: 'none',
                          }}
                        >
                          <input
                            type="checkbox"
                            checked={selectedScopes.includes(scope)}
                            onChange={() => handleScopeToggle(scope)}
                          />
                          <code>{scope}</code>
                        </label>
                      )
                    )}
                  </div>
                </div>

                <button
                  type="submit"
                  className="btn btn-primary btn-sm"
                  disabled={createClientMutation.isPending || !newClientName}
                >
                  {createClientMutation.isPending ? <span className="spinner" /> : 'Generate Client'}
                </button>
              </form>
            )}
          </div>
        )}

        {/* ─── API Clients Table ─── */}
        <table style={{ marginTop: '1rem' }}>
          <thead>
            <tr>
              <th>Client Name</th>
              <th>Client ID</th>
              <th>Granted Scopes</th>
              <th>Status</th>
              <th>Issued At</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {apiClients.map((client: ApiClient) => (
              <tr key={client.id}>
                <td style={{ fontWeight: 600 }}>{client.clientName}</td>
                <td style={{ fontFamily: 'monospace', fontSize: '0.85rem' }}>{client.clientId}</td>
                <td>
                  <div style={{ display: 'flex', gap: '0.3rem', flexWrap: 'wrap' }}>
                    {client.scopes.map((s: string) => (
                      <span key={s} className="pill pill-pending" style={{ fontSize: '0.72rem' }}>
                        {s}
                      </span>
                    ))}
                  </div>
                </td>
                <td>
                  <span
                    className={`pill ${client.status === 'ACTIVE' ? 'pill-success' : 'pill-failed'}`}
                  >
                    {client.status}
                  </span>
                </td>
                <td>{formatDate(client.createdAt)}</td>
                <td>
                  {client.status === 'ACTIVE' && (
                    <button
                      className="btn btn-ghost btn-sm"
                      style={{ fontSize: '0.75rem', color: 'var(--color-error)' }}
                      onClick={() => {
                        if (confirm(`Revoke client ${client.clientId}?`)) {
                          revokeClientMutation.mutate(client.clientId);
                        }
                      }}
                    >
                      Revoke
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}

function formatDate(iso?: string): string {
  if (!iso) return '—';
  try {
    return new Date(iso).toLocaleDateString([], {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    });
  } catch {
    return iso;
  }
}
