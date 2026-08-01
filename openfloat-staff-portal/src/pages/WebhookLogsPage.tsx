import React from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { fetchFailedWebhooks, redriveWebhook } from '../api/queries';

export default function WebhookLogsPage() {
  const queryClient = useQueryClient();

  const { data: failedLogs = [], isLoading } = useQuery({
    queryKey: ['failed-webhooks'],
    queryFn: fetchFailedWebhooks,
  });

  const redriveMutation = useMutation({
    mutationFn: redriveWebhook,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['failed-webhooks'] });
    },
  });

  return (
    <div className="webhook-logs-page">
      <div className="toolbar" style={{ marginBottom: '1.5rem' }}>
        <h3>Webhook Delivery & Redrive Console</h3>
        <p className="text-muted">Monitor outbound webhooks dispatched to client applications and redrive failed dispatches</p>
      </div>

      <div className="card">
        {isLoading ? (
          <p>Loading webhook delivery logs...</p>
        ) : failedLogs.length === 0 ? (
          <div style={{ padding: '1.5rem', textAlign: 'center' }}>
            <p className="text-muted" style={{ margin: 0 }}>No failed webhook deliveries detected. All outbound client notifications are healthy.</p>
          </div>
        ) : (
          <table className="data-table">
            <thead>
              <tr>
                <th>Target Client</th>
                <th>Account Reference</th>
                <th>Target Webhook URL</th>
                <th>HTTP Status</th>
                <th>Attempt #</th>
                <th>Error Message</th>
                <th>Time</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {failedLogs.map((log) => (
                <tr key={log.id}>
                  <td><strong>{log.clientName}</strong></td>
                  <td><code>{log.accountReference || '—'}</code></td>
                  <td><small>{log.targetUrl}</small></td>
                  <td>
                    <span className="status-badge failed">
                      {log.httpStatus ?? 'ERR'}
                    </span>
                  </td>
                  <td>{log.attemptNumber}</td>
                  <td><small style={{ color: 'var(--color-warning)' }}>{log.errorMessage || 'Unknown Error'}</small></td>
                  <td>{new Date(log.createdAt).toLocaleString()}</td>
                  <td>
                    <button
                      className="btn btn-primary btn-sm"
                      disabled={redriveMutation.isPending}
                      onClick={() => redriveMutation.mutate(log.id)}
                    >
                      Redrive
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
