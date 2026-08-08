import React from 'react';
import { useMutation } from '@tanstack/react-query';
import { uploadBulkPayoutCsv, submitBulkPayout } from '../api/queries';
import type { BulkPayoutResult, BulkPayoutItemResult } from '../types/domain';

/* ── CSV Parser ────────────────────────────────────────
   Expected CSV format (header row required):
   msisdn,amount,accountReference,remarks
──────────────────────────────────────────────────────── */
interface ParsedRow {
  msisdn: string;
  amount: number;
  accountReference: string;
  remarks?: string;
}

function parseCsvText(text: string): { rows: ParsedRow[]; errors: string[] } {
  const lines = text.split('\n').map((l) => l.trim()).filter(Boolean);
  if (lines.length < 2) return { rows: [], errors: ['CSV must have a header row and at least one data row.'] };

  const header = lines[0].toLowerCase().split(',').map((h) => h.trim());
  const msisdnIdx = header.indexOf('msisdn');
  const amtIdx = header.indexOf('amount');
  const refIdx = header.indexOf('accountreference');
  const remarksIdx = header.indexOf('remarks');

  if (msisdnIdx < 0 || amtIdx < 0 || refIdx < 0) {
    return { rows: [], errors: ['CSV headers must include: msisdn, amount, accountReference'] };
  }

  const rows: ParsedRow[] = [];
  const errors: string[] = [];

  lines.slice(1).forEach((line, i) => {
    const cols = line.split(',').map((c) => c.trim());
    const msisdn = cols[msisdnIdx] ?? '';
    const amount = parseFloat(cols[amtIdx] ?? '0');
    const accountReference = cols[refIdx] ?? '';
    const remarks = remarksIdx >= 0 ? cols[remarksIdx] : undefined;

    if (!msisdn || isNaN(amount) || amount <= 0 || !accountReference) {
      errors.push(`Row ${i + 2}: Invalid data (msisdn="${msisdn}", amount=${cols[amtIdx]}, ref="${accountReference}")`);
      return;
    }
    rows.push({ msisdn, amount, accountReference, remarks });
  });

  return { rows, errors };
}

/* ── Result Summary Card ──────────────────────────────── */
function ResultCard({ result }: { result: BulkPayoutResult }) {
  const [showDetails, setShowDetails] = React.useState(false);
  const successRate = result.totalCount > 0
    ? Math.round((result.successfulCount / result.totalCount) * 100)
    : 0;

  return (
    <div className="card" style={{ marginTop: '1.5rem' }}>
      <h4 style={{ margin: '0 0 1rem' }}>Batch Result</h4>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '1rem', marginBottom: '1rem' }}>
        {[
          { label: 'Total', value: result.totalCount, color: 'var(--color-text)' },
          { label: 'Successful', value: result.successfulCount, color: '#10b981' },
          { label: 'Failed', value: result.failedCount, color: '#ef4444' },
          { label: 'Success Rate', value: `${successRate}%`, color: successRate >= 80 ? '#10b981' : '#f59e0b' },
        ].map(({ label, value, color }) => (
          <div key={label} className="card" style={{ textAlign: 'center', padding: '0.75rem' }}>
            <div style={{ fontSize: '0.75rem', color: 'var(--color-text-muted)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>{label}</div>
            <div style={{ fontSize: '1.6rem', fontWeight: 700, color }}>{value}</div>
          </div>
        ))}
      </div>

      <div style={{ fontSize: '0.85rem', color: 'var(--color-text-muted)', marginBottom: '0.75rem' }}>
        Total Amount Disbursed: <strong>KES {result.totalAmount.toLocaleString()}</strong>
        {' · '}
        Processed at: {new Date(result.processedAt).toLocaleString()}
      </div>

      <button className="btn btn-ghost btn-sm" onClick={() => setShowDetails((v) => !v)}>
        {showDetails ? '▲ Hide' : '▼ Show'} item-by-item results
      </button>

      {showDetails && (
        <div style={{ marginTop: '1rem', maxHeight: 320, overflowY: 'auto' }}>
          <table>
            <thead>
              <tr>
                <th>MSISDN</th>
                <th>Amount</th>
                <th>Ref</th>
                <th>Status</th>
                <th>Transaction ID / Error</th>
              </tr>
            </thead>
            <tbody>
              {result.results.map((item: BulkPayoutItemResult, idx) => (
                <tr key={idx}>
                  <td style={{ fontFamily: 'monospace' }}>{item.msisdn}</td>
                  <td>KES {item.amount.toLocaleString()}</td>
                  <td style={{ fontFamily: 'monospace', fontSize: '0.8rem' }}>{item.accountReference}</td>
                  <td>
                    <span className={`pill ${item.success ? 'pill-success' : 'pill-failed'}`}>
                      {item.success ? 'OK' : 'FAILED'}
                    </span>
                  </td>
                  <td style={{ fontSize: '0.8rem', color: 'var(--color-text-muted)' }}>
                    {item.transactionId ?? item.errorMessage ?? '—'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

/* ── Main Page ────────────────────────────────────────── */
export default function BulkPayoutsPage() {
  const [mode, setMode] = React.useState<'csv' | 'manual'>('csv');

  // CSV mode state
  const [csvFile, setCsvFile] = React.useState<File | null>(null);
  const [csvPreview, setCsvPreview] = React.useState<ParsedRow[]>([]);
  const [csvErrors, setCsvErrors] = React.useState<string[]>([]);
  const [dragOver, setDragOver] = React.useState(false);

  // Manual mode state
  const [manualRows, setManualRows] = React.useState([
    { msisdn: '', amount: '', accountReference: '', remarks: '' },
  ]);

  const [result, setResult] = React.useState<BulkPayoutResult | null>(null);
  const [submitError, setSubmitError] = React.useState('');

  /* CSV mutation */
  const csvMutation = useMutation({
    mutationFn: (file: File) => uploadBulkPayoutCsv(file),
    onSuccess: (res) => { setResult(res); setSubmitError(''); },
    onError: (err: unknown) => setSubmitError(err instanceof Error ? err.message : 'Upload failed.'),
  });

  /* Manual mutation */
  const manualMutation = useMutation({
    mutationFn: (items: typeof manualRows) =>
      submitBulkPayout(
        items.map((r) => ({
          msisdn: r.msisdn.trim(),
          amount: parseFloat(r.amount),
          accountReference: r.accountReference.trim(),
          remarks: r.remarks.trim() || undefined,
        }))
      ),
    onSuccess: (res) => { setResult(res); setSubmitError(''); },
    onError: (err: unknown) => setSubmitError(err instanceof Error ? err.message : 'Submission failed.'),
  });

  const isLoading = csvMutation.isPending || manualMutation.isPending;

  /* CSV file handling */
  function handleFileSelect(file: File) {
    setCsvFile(file);
    setResult(null);
    setSubmitError('');
    const reader = new FileReader();
    reader.onload = (e) => {
      const text = e.target?.result as string;
      const { rows, errors } = parseCsvText(text);
      setCsvPreview(rows);
      setCsvErrors(errors);
    };
    reader.readAsText(file);
  }

  function handleDrop(e: React.DragEvent<HTMLDivElement>) {
    e.preventDefault();
    setDragOver(false);
    const file = e.dataTransfer.files[0];
    if (file && (file.name.endsWith('.csv') || file.type === 'text/csv')) {
      handleFileSelect(file);
    }
  }

  /* Manual row helpers */
  function updateManualRow(idx: number, field: string, value: string) {
    setManualRows((prev) => prev.map((r, i) => i === idx ? { ...r, [field]: value } : r));
  }
  function addManualRow() {
    setManualRows((prev) => [...prev, { msisdn: '', amount: '', accountReference: '', remarks: '' }]);
  }
  function removeManualRow(idx: number) {
    setManualRows((prev) => prev.filter((_, i) => i !== idx));
  }

  /* Download sample CSV */
  function downloadSampleCsv() {
    const content = [
      'msisdn,amount,accountReference,remarks',
      '254712345678,1000,SALARY-JUL-001,July salary',
      '254723456789,2500,SALARY-JUL-002,July salary',
      '254734567890,750,VENDOR-PAY-001,Supplier payment',
    ].join('\n');
    const blob = new Blob([content], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'bulk-payout-sample.csv';
    a.click();
    URL.revokeObjectURL(url);
  }

  return (
    <>
      {/* ─── Header ─── */}
      <div className="card" style={{ marginBottom: '1.5rem' }}>
        <div className="toolbar">
          <div>
            <h3 style={{ margin: 0 }}>Bulk B2C Payout Console</h3>
            <p style={{ margin: '0.25rem 0 0', fontSize: '0.85rem', color: 'var(--color-text-muted)' }}>
              Disburse funds to multiple M-Pesa recipients via CSV upload or manual entry.
            </p>
          </div>
          <button className="btn btn-ghost btn-sm" onClick={downloadSampleCsv}>
            Download Sample CSV
          </button>
        </div>

        {/* Mode Toggle */}
        <div style={{ display: 'flex', gap: '0.5rem', marginTop: '1rem' }}>
          {(['csv', 'manual'] as const).map((m) => (
            <button
              key={m}
              className={`btn btn-sm ${mode === m ? 'btn-primary' : 'btn-secondary'}`}
              onClick={() => { setMode(m); setResult(null); setSubmitError(''); }}
            >
              {m === 'csv' ? 'CSV Upload' : 'Manual Entry'}
            </button>
          ))}
        </div>
      </div>

      {/* ─── CSV Upload Mode ─── */}
      {mode === 'csv' && (
        <div className="card" style={{ marginBottom: '1rem' }}>
          {/* Dropzone */}
          <div
            onDragOver={(e) => { e.preventDefault(); setDragOver(true); }}
            onDragLeave={() => setDragOver(false)}
            onDrop={handleDrop}
            style={{
              border: `2px dashed ${dragOver ? 'var(--color-primary)' : 'var(--color-border)'}`,
              borderRadius: 8,
              padding: '3rem 2rem',
              textAlign: 'center',
              transition: 'border-color 0.2s, background 0.2s',
              background: dragOver ? 'rgba(99,102,241,0.05)' : 'transparent',
              cursor: 'pointer',
            }}
            onClick={() => document.getElementById('csv-file-input')?.click()}
          >
            <div style={{ fontSize: '2rem', fontWeight: 300, color: 'var(--color-text-muted)', marginBottom: '0.5rem', letterSpacing: '0.1em' }}>↑ CSV</div>
            <div style={{ fontSize: '1rem', fontWeight: 600, marginBottom: '0.25rem' }}>
              {csvFile ? csvFile.name : 'Drag & drop CSV here, or click to browse'}
            </div>
            <div style={{ fontSize: '0.8rem', color: 'var(--color-text-muted)' }}>
              Required columns: <code>msisdn, amount, accountReference</code> — Optional: <code>remarks</code>
            </div>
            <input
              id="csv-file-input"
              type="file"
              accept=".csv,text/csv"
              style={{ display: 'none' }}
              onChange={(e) => { const f = e.target.files?.[0]; if (f) handleFileSelect(f); }}
            />
          </div>

          {/* CSV Errors */}
          {csvErrors.length > 0 && (
            <div style={{ marginTop: '1rem', padding: '0.75rem', background: 'rgba(239,68,68,0.1)', borderRadius: 6 }}>
              <strong style={{ color: '#ef4444' }}>Parse Errors:</strong>
              <ul style={{ margin: '0.5rem 0 0', paddingLeft: '1.25rem', fontSize: '0.8rem', color: '#ef4444' }}>
                {csvErrors.map((e, i) => <li key={i}>{e}</li>)}
              </ul>
            </div>
          )}

          {/* CSV Preview */}
          {csvPreview.length > 0 && (
            <div style={{ marginTop: '1rem' }}>
              <div style={{ fontSize: '0.85rem', color: 'var(--color-text-muted)', marginBottom: '0.5rem' }}>
                Preview — {csvPreview.length} valid recipient{csvPreview.length !== 1 ? 's' : ''}
                {' · '}
                Total: <strong>KES {csvPreview.reduce((s, r) => s + r.amount, 0).toLocaleString()}</strong>
              </div>
              <div style={{ maxHeight: 240, overflowY: 'auto' }}>
                <table>
                  <thead>
                    <tr><th>MSISDN</th><th>Amount</th><th>Ref</th><th>Remarks</th></tr>
                  </thead>
                  <tbody>
                    {csvPreview.slice(0, 20).map((r, i) => (
                      <tr key={i}>
                        <td style={{ fontFamily: 'monospace' }}>{r.msisdn}</td>
                        <td>KES {r.amount.toLocaleString()}</td>
                        <td style={{ fontFamily: 'monospace', fontSize: '0.8rem' }}>{r.accountReference}</td>
                        <td style={{ fontSize: '0.8rem', color: 'var(--color-text-muted)' }}>{r.remarks ?? '—'}</td>
                      </tr>
                    ))}
                    {csvPreview.length > 20 && (
                      <tr><td colSpan={4} style={{ textAlign: 'center', color: 'var(--color-text-muted)', fontSize: '0.8rem' }}>
                        … and {csvPreview.length - 20} more rows
                      </td></tr>
                    )}
                  </tbody>
                </table>
              </div>

              {submitError && (
                <div style={{ color: '#ef4444', fontSize: '0.85rem', marginTop: '0.75rem' }}>{submitError}</div>
              )}

              <div style={{ marginTop: '1rem' }}>
                <button
                  className="btn btn-primary"
                  disabled={isLoading || csvErrors.length > 0 || !csvFile}
                  onClick={() => csvFile && csvMutation.mutate(csvFile)}
                >
                  {isLoading ? 'Sending…' : `Disburse to ${csvPreview.length} Recipients`}
                </button>
              </div>
            </div>
          )}
        </div>
      )}

      {/* ─── Manual Entry Mode ─── */}
      {mode === 'manual' && (
        <div className="card" style={{ marginBottom: '1rem' }}>
          <div className="toolbar" style={{ marginBottom: '0.75rem' }}>
            <span style={{ fontSize: '0.85rem', fontWeight: 600 }}>
              Recipients ({manualRows.length})
              {' · '}
              Total: KES {manualRows.reduce((s, r) => s + (parseFloat(r.amount) || 0), 0).toLocaleString()}
            </span>
            <button className="btn btn-ghost btn-sm" onClick={addManualRow}>+ Add Row</button>
          </div>
          <table>
            <thead>
              <tr>
                <th>MSISDN *</th>
                <th>Amount (KES) *</th>
                <th>Account Reference *</th>
                <th>Remarks</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {manualRows.map((row, idx) => (
                <tr key={idx}>
                  <td>
                    <input
                      className="form-input"
                      style={{ width: '100%' }}
                      value={row.msisdn}
                      onChange={(e) => updateManualRow(idx, 'msisdn', e.target.value)}
                      placeholder="254712345678"
                    />
                  </td>
                  <td>
                    <input
                      type="number"
                      className="form-input"
                      style={{ width: 120 }}
                      value={row.amount}
                      min={1}
                      onChange={(e) => updateManualRow(idx, 'amount', e.target.value)}
                      placeholder="1000"
                    />
                  </td>
                  <td>
                    <input
                      className="form-input"
                      style={{ width: '100%' }}
                      value={row.accountReference}
                      onChange={(e) => updateManualRow(idx, 'accountReference', e.target.value)}
                      placeholder="PAY-REF-001"
                    />
                  </td>
                  <td>
                    <input
                      className="form-input"
                      style={{ width: '100%' }}
                      value={row.remarks}
                      onChange={(e) => updateManualRow(idx, 'remarks', e.target.value)}
                      placeholder="Optional"
                    />
                  </td>
                  <td>
                    {manualRows.length > 1 && (
                      <button
                        className="btn btn-ghost btn-sm"
                        style={{ color: '#ef4444' }}
                        onClick={() => removeManualRow(idx)}
                      >Remove</button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          {submitError && (
            <div style={{ color: '#ef4444', fontSize: '0.85rem', marginTop: '0.75rem' }}>{submitError}</div>
          )}

          <div style={{ marginTop: '1rem' }}>
            <button
              className="btn btn-primary"
              disabled={isLoading}
              onClick={() => {
                const valid = manualRows.every((r) => r.msisdn.trim() && parseFloat(r.amount) > 0 && r.accountReference.trim());
                if (!valid) { setSubmitError('All rows must have MSISDN, amount > 0, and account reference.'); return; }
                setSubmitError('');
                manualMutation.mutate(manualRows);
              }}
            >
              {isLoading ? 'Sending…' : `Disburse to ${manualRows.length} Recipient${manualRows.length !== 1 ? 's' : ''}`}
            </button>
          </div>
        </div>
      )}

      {/* ─── Result ─── */}
      {result && <ResultCard result={result} />}
    </>
  );
}
