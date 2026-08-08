import React from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  fetchInvoices,
  createInvoice,
  cancelInvoice,
  issueInvoice,
} from '../api/queries';
import type { Invoice, InvoiceStatus } from '../types/domain';

const STATUS_OPTIONS: string[] = ['ALL', 'DRAFT', 'ISSUED', 'PAID', 'CANCELLED'];

function statusPillClass(status: InvoiceStatus): string {
  switch (status) {
    case 'PAID': return 'pill-success';
    case 'ISSUED': return 'pill-pending';
    case 'DRAFT': return '';
    case 'CANCELLED': return 'pill-failed';
    default: return '';
  }
}

function fmtDate(iso?: string): string {
  if (!iso) return '—';
  try {
    return new Date(iso).toLocaleDateString([], { day: '2-digit', month: 'short', year: 'numeric' });
  } catch {
    return iso;
  }
}

/* ── Create Invoice Modal ────────────────────────────── */
interface LineItemRow {
  description: string;
  quantity: number;
  unitPrice: number;
}

function CreateInvoiceModal({ onClose, onCreated }: { onClose: () => void; onCreated: (inv: Invoice) => void }) {
  const [customerName, setCustomerName] = React.useState('');
  const [customerMsisdn, setCustomerMsisdn] = React.useState('');
  const [dueDate, setDueDate] = React.useState('');
  const [notes, setNotes] = React.useState('');
  const [lineItems, setLineItems] = React.useState<LineItemRow[]>([
    { description: '', quantity: 1, unitPrice: 0 },
  ]);
  const [error, setError] = React.useState('');
  const [loading, setLoading] = React.useState(false);

  const totalAmount = lineItems.reduce((s, li) => s + li.quantity * li.unitPrice, 0);

  function updateLineItem(idx: number, field: keyof LineItemRow, value: string | number) {
    setLineItems((prev) =>
      prev.map((li, i) => i === idx ? { ...li, [field]: value } : li)
    );
  }

  function addLine() {
    setLineItems((prev) => [...prev, { description: '', quantity: 1, unitPrice: 0 }]);
  }

  function removeLine(idx: number) {
    setLineItems((prev) => prev.filter((_, i) => i !== idx));
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError('');
    if (!customerName.trim() || !customerMsisdn.trim()) {
      setError('Customer name and phone number are required.');
      return;
    }
    if (lineItems.some((li) => !li.description.trim() || li.quantity <= 0)) {
      setError('All line items must have a description and quantity ≥ 1.');
      return;
    }
    setLoading(true);
    try {
      const inv = await createInvoice({
        customerName: customerName.trim(),
        customerMsisdn: customerMsisdn.trim(),
        lineItems: lineItems.map((li) => ({
          description: li.description,
          quantity: li.quantity,
          unitPrice: li.unitPrice,
        })),
        dueDate: dueDate || undefined,
        notes: notes.trim() || undefined,
      });
      onCreated(inv);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to create invoice.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div
      style={{
        position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.55)', display: 'flex',
        alignItems: 'flex-start', justifyContent: 'center', zIndex: 200, overflowY: 'auto', padding: '2rem 1rem',
      }}
      onClick={(e) => e.target === e.currentTarget && onClose()}
    >
      <div className="card" style={{ width: '100%', maxWidth: 680 }}>
        <div className="toolbar" style={{ marginBottom: '1.25rem' }}>
          <h3 style={{ margin: 0 }}>Create Invoice</h3>
          <button className="btn btn-ghost btn-sm" onClick={onClose}>✕ Close</button>
        </div>

        <form onSubmit={handleSubmit}>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem', marginBottom: '1rem' }}>
            <label>
              <div style={{ fontSize: '0.8rem', color: 'var(--color-text-muted)', marginBottom: 4 }}>Customer Name *</div>
              <input
                className="form-input"
                style={{ width: '100%' }}
                value={customerName}
                onChange={(e) => setCustomerName(e.target.value)}
                placeholder="e.g. Kamau Enterprises Ltd"
                required
              />
            </label>
            <label>
              <div style={{ fontSize: '0.8rem', color: 'var(--color-text-muted)', marginBottom: 4 }}>Customer Phone (254...) *</div>
              <input
                className="form-input"
                style={{ width: '100%' }}
                value={customerMsisdn}
                onChange={(e) => setCustomerMsisdn(e.target.value)}
                placeholder="254712345678"
                required
              />
            </label>
            <label>
              <div style={{ fontSize: '0.8rem', color: 'var(--color-text-muted)', marginBottom: 4 }}>Due Date</div>
              <input
                type="date"
                className="form-input"
                style={{ width: '100%' }}
                value={dueDate}
                onChange={(e) => setDueDate(e.target.value)}
              />
            </label>
            <label>
              <div style={{ fontSize: '0.8rem', color: 'var(--color-text-muted)', marginBottom: 4 }}>Notes</div>
              <input
                className="form-input"
                style={{ width: '100%' }}
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
                placeholder="Optional notes"
              />
            </label>
          </div>

          {/* Line Items */}
          <div style={{ marginBottom: '1rem' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
              <span style={{ fontSize: '0.85rem', fontWeight: 600 }}>Line Items</span>
              <button type="button" className="btn btn-ghost btn-sm" onClick={addLine}>+ Add Line</button>
            </div>
            <table>
              <thead>
                <tr>
                  <th style={{ width: '50%' }}>Description</th>
                  <th>Qty</th>
                  <th>Unit Price (KES)</th>
                  <th>Subtotal</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {lineItems.map((li, idx) => (
                  <tr key={idx}>
                    <td>
                      <input
                        className="form-input"
                        style={{ width: '100%' }}
                        value={li.description}
                        onChange={(e) => updateLineItem(idx, 'description', e.target.value)}
                        placeholder="Service / item description"
                        required
                      />
                    </td>
                    <td>
                      <input
                        type="number"
                        className="form-input"
                        style={{ width: 70 }}
                        value={li.quantity}
                        min={1}
                        onChange={(e) => updateLineItem(idx, 'quantity', parseInt(e.target.value) || 1)}
                      />
                    </td>
                    <td>
                      <input
                        type="number"
                        className="form-input"
                        style={{ width: 120 }}
                        value={li.unitPrice}
                        min={0}
                        step={0.01}
                        onChange={(e) => updateLineItem(idx, 'unitPrice', parseFloat(e.target.value) || 0)}
                      />
                    </td>
                    <td style={{ fontFamily: 'monospace', textAlign: 'right' }}>
                      {(li.quantity * li.unitPrice).toLocaleString()}
                    </td>
                    <td>
                      {lineItems.length > 1 && (
                        <button type="button" className="btn btn-ghost btn-sm" onClick={() => removeLine(idx)}
                          style={{ color: 'var(--color-danger)' }}>✕</button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
              <tfoot>
                <tr>
                  <td colSpan={3} style={{ textAlign: 'right', fontWeight: 600 }}>Total</td>
                  <td style={{ fontFamily: 'monospace', fontWeight: 700, textAlign: 'right' }}>
                    KES {totalAmount.toLocaleString()}
                  </td>
                  <td></td>
                </tr>
              </tfoot>
            </table>
          </div>

          {error && (
            <div style={{ color: 'var(--color-danger)', fontSize: '0.85rem', marginBottom: '0.75rem' }}>
              ⚠ {error}
            </div>
          )}

          <div className="toolbar-actions">
            <button type="button" className="btn btn-secondary" onClick={onClose}>Cancel</button>
            <button type="submit" className="btn btn-primary" disabled={loading}>
              {loading ? 'Creating…' : '✓ Create Invoice'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

/* ── Main Page ───────────────────────────────────────── */
export default function InvoicesPage() {
  const qc = useQueryClient();
  const [statusFilter, setStatusFilter] = React.useState('ALL');
  const [searchTerm, setSearchTerm] = React.useState('');
  const [showCreate, setShowCreate] = React.useState(false);
  const [actionLoading, setActionLoading] = React.useState<string | null>(null);

  const { data: invoices = [], isLoading } = useQuery({
    queryKey: ['invoices'],
    queryFn: () => fetchInvoices(),
    refetchInterval: 30_000,
  });

  const filtered = React.useMemo(() => {
    return invoices.filter((inv: Invoice) => {
      if (statusFilter !== 'ALL' && inv.status !== statusFilter) return false;
      if (searchTerm) {
        const term = searchTerm.toLowerCase();
        const s = [inv.invoiceNumber, inv.customerName, inv.customerMsisdn].join(' ').toLowerCase();
        if (!s.includes(term)) return false;
      }
      return true;
    });
  }, [invoices, statusFilter, searchTerm]);

  // Summary stats
  const totalIssued = invoices.filter((i: Invoice) => i.status === 'ISSUED').reduce((s: number, i: Invoice) => s + i.totalAmount, 0);
  const totalPaid = invoices.filter((i: Invoice) => i.status === 'PAID').reduce((s: number, i: Invoice) => s + i.totalAmount, 0);
  const draftCount = invoices.filter((i: Invoice) => i.status === 'DRAFT').length;

  async function handleIssue(inv: Invoice) {
    setActionLoading(inv.id);
    try {
      await issueInvoice(inv.id);
      qc.invalidateQueries({ queryKey: ['invoices'] });
    } catch {
      alert('Failed to issue invoice. Please try again.');
    } finally {
      setActionLoading(null);
    }
  }

  async function handleCancel(inv: Invoice) {
    if (!confirm(`Cancel invoice ${inv.invoiceNumber}? This cannot be undone.`)) return;
    setActionLoading(inv.id);
    try {
      await cancelInvoice(inv.id);
      qc.invalidateQueries({ queryKey: ['invoices'] });
    } catch {
      alert('Failed to cancel invoice. Please try again.');
    } finally {
      setActionLoading(null);
    }
  }

  return (
    <>
      {showCreate && (
        <CreateInvoiceModal
          onClose={() => setShowCreate(false)}
          onCreated={() => {
            setShowCreate(false);
            qc.invalidateQueries({ queryKey: ['invoices'] });
          }}
        />
      )}

      {/* ─── Summary Strip ─── */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '1rem', marginBottom: '1.5rem' }}>
        {[
          { label: 'Outstanding (Issued)', value: `KES ${totalIssued.toLocaleString()}`, accent: '#f59e0b' },
          { label: 'Collected (Paid)', value: `KES ${totalPaid.toLocaleString()}`, accent: '#10b981' },
          { label: 'Drafts Pending', value: draftCount.toString(), accent: '#6b7280' },
        ].map(({ label, value, accent }) => (
          <div
            key={label}
            className="card"
            style={{ borderLeft: `4px solid ${accent}`, padding: '1rem 1.25rem' }}
          >
            <div style={{ fontSize: '0.75rem', color: 'var(--color-text-muted)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>{label}</div>
            <div style={{ fontSize: '1.5rem', fontWeight: 700, marginTop: '0.25rem' }}>{value}</div>
          </div>
        ))}
      </div>

      {/* ─── Toolbar ─── */}
      <div className="card" style={{ marginBottom: '1rem' }}>
        <div className="toolbar">
          <h3>
            Invoices
            <span style={{ fontSize: '0.8rem', fontWeight: 400, color: 'var(--color-text-muted)', marginLeft: '0.75rem' }}>
              {filtered.length} result{filtered.length !== 1 ? 's' : ''}
            </span>
          </h3>
          <div className="toolbar-actions">
            <button className="btn btn-primary btn-sm" onClick={() => setShowCreate(true)}>
              + New Invoice
            </button>
          </div>
        </div>

        <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap', marginTop: '0.75rem' }}>
          <input
            className="form-input"
            style={{ maxWidth: 280 }}
            placeholder="Search number, name, phone…"
            value={searchTerm}
            onChange={(e: React.ChangeEvent<HTMLInputElement>) => setSearchTerm(e.target.value)}
          />
          <select
            className="form-input"
            style={{ maxWidth: 160 }}
            value={statusFilter}
            onChange={(e: React.ChangeEvent<HTMLSelectElement>) => setStatusFilter(e.target.value)}
          >
            {STATUS_OPTIONS.map((s) => (
              <option key={s} value={s}>{s === 'ALL' ? 'All Statuses' : s}</option>
            ))}
          </select>
        </div>
      </div>

      {/* ─── Table ─── */}
      <div className="card">
        {isLoading ? (
          <div className="loading-state"><span className="spinner" /> Loading invoices…</div>
        ) : filtered.length === 0 ? (
          <div className="loading-state">No invoices match your filters.</div>
        ) : (
          <table>
            <thead>
              <tr>
                <th>Invoice #</th>
                <th>Customer</th>
                <th>Phone</th>
                <th>Amount</th>
                <th>Status</th>
                <th>Due Date</th>
                <th>Issued / Paid</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((inv: Invoice) => (
                <tr key={inv.id}>
                  <td style={{ fontFamily: 'monospace', fontSize: '0.85rem' }}>{inv.invoiceNumber}</td>
                  <td style={{ fontWeight: 500 }}>{inv.customerName}</td>
                  <td style={{ fontFamily: 'monospace', fontSize: '0.85rem' }}>{inv.customerMsisdn}</td>
                  <td style={{ fontFamily: 'monospace' }}>KES {inv.totalAmount.toLocaleString()}</td>
                  <td>
                    <span className={`pill ${statusPillClass(inv.status)}`}>{inv.status}</span>
                  </td>
                  <td>{fmtDate(inv.dueDate)}</td>
                  <td style={{ fontSize: '0.8rem', color: 'var(--color-text-muted)' }}>
                    {inv.paidAt ? `Paid ${fmtDate(inv.paidAt)}` : inv.issuedAt ? `Issued ${fmtDate(inv.issuedAt)}` : '—'}
                  </td>
                  <td>
                    <div className="toolbar-actions" style={{ gap: '0.4rem' }}>
                      {inv.status === 'DRAFT' && (
                        <button
                          className="btn btn-primary btn-sm"
                          disabled={actionLoading === inv.id}
                          onClick={() => handleIssue(inv)}
                        >
                          {actionLoading === inv.id ? '…' : 'Issue'}
                        </button>
                      )}
                      {(inv.status === 'DRAFT' || inv.status === 'ISSUED') && (
                        <button
                          className="btn btn-secondary btn-sm"
                          style={{ color: 'var(--color-danger)' }}
                          disabled={actionLoading === inv.id}
                          onClick={() => handleCancel(inv)}
                        >
                          {actionLoading === inv.id ? '…' : 'Cancel'}
                        </button>
                      )}
                      {(inv.status === 'PAID' || inv.status === 'CANCELLED') && (
                        <span style={{ fontSize: '0.8rem', color: 'var(--color-text-muted)' }}>—</span>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </>
  );
}
