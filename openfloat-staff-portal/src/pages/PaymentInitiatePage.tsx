import React from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery } from '@tanstack/react-query';
import { initiateStkPush, pollTransactionStatus } from '../api/queries';
import type { StkPushPayload, StkPushResult } from '../api/queries';
import type { Transaction } from '../types/domain';

const stkSchema = z.object({
  msisdn: z
    .string()
    .min(10, 'Phone number must be at least 10 digits')
    .max(15, 'Phone number too long')
    .regex(/^[0-9+]+$/, 'Invalid phone number format'),
  amount: z.coerce
    .number()
    .positive('Amount must be positive')
    .min(1, 'Minimum amount is KES 1')
    .max(150_000, 'Maximum STK amount is KES 150,000'),
  accountReference: z
    .string()
    .min(2, 'Account reference is required')
    .max(20, 'Account reference too long'),
  paybill: z
    .string()
    .min(5, 'Valid paybill/till number required')
    .max(10, 'Paybill too long'),
});

type StkFormData = z.infer<typeof stkSchema>;

export default function PaymentInitiatePage() {
  const [activeResult, setActiveResult] = React.useState<StkPushResult | null>(null);
  const [pollingTxId, setPollingTxId] = React.useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors },
    reset,
  } = useForm<StkFormData>({
    resolver: zodResolver(stkSchema),
    defaultValues: { paybill: '174379' },
  });

  const mutation = useMutation({
    mutationFn: (data: StkPushPayload) => initiateStkPush(data),
    onSuccess: (result) => {
      setActiveResult(result);
      if (result.transactionId) {
        setPollingTxId(result.transactionId);
      }
    },
  });

  // Live status polling — polls every 3s while a transaction is pending
  const pollingQuery = useQuery({
    queryKey: ['poll-stk', pollingTxId],
    queryFn: () => pollTransactionStatus(pollingTxId!),
    enabled: !!pollingTxId,
    refetchInterval: (query) => {
      const data = query.state.data as Transaction | undefined;
      if (data && data.status !== 'PENDING') {
        return false; // Stop polling once resolved
      }
      return 3_000;
    },
  });

  const liveStatus = pollingQuery.data;
  const isResolved = liveStatus && liveStatus.status !== 'PENDING';

  return (
    <div className="card" style={{ maxWidth: 720 }}>
      <div className="toolbar">
        <h3>Initiate STK Push</h3>
        {pollingTxId && !isResolved && (
          <span className="live-indicator">
            <span className="dot" /> Polling status…
          </span>
        )}
      </div>

      <form
        onSubmit={handleSubmit((data) => {
          setActiveResult(null);
          setPollingTxId(null);
          mutation.mutate(data);
        })}
      >
        <div className="form-row">
          <div className="form-group">
            <label htmlFor="msisdn">Phone Number (MSISDN)</label>
            <input
              id="msisdn"
              className="form-input"
              placeholder="254712345678"
              {...register('msisdn')}
            />
            {errors.msisdn && <span className="form-error">{errors.msisdn.message}</span>}
          </div>

          <div className="form-group">
            <label htmlFor="amount">Amount (KES)</label>
            <input
              id="amount"
              className="form-input"
              type="number"
              placeholder="100"
              {...register('amount')}
            />
            {errors.amount && <span className="form-error">{errors.amount.message}</span>}
          </div>
        </div>

        <div className="form-row">
          <div className="form-group">
            <label htmlFor="accountReference">Account Reference</label>
            <input
              id="accountReference"
              className="form-input"
              placeholder="INV-2026-001"
              {...register('accountReference')}
            />
            {errors.accountReference && (
              <span className="form-error">{errors.accountReference.message}</span>
            )}
          </div>

          <div className="form-group">
            <label htmlFor="paybill">Paybill / Till Number</label>
            <input
              id="paybill"
              className="form-input"
              placeholder="174379"
              {...register('paybill')}
            />
            {errors.paybill && <span className="form-error">{errors.paybill.message}</span>}
          </div>
        </div>

        <button
          type="submit"
          className="btn btn-primary"
          disabled={mutation.isPending}
        >
          {mutation.isPending ? (
            <>
              <span className="spinner" /> Sending…
            </>
          ) : (
            '📲 Send STK Prompt'
          )}
        </button>
      </form>

      {/* ─── Mutation Error ─── */}
      {mutation.isError && (
        <div className="result-block error">
          ❌ Error: {(mutation.error as Error)?.message ?? 'Failed to initiate STK Push'}
        </div>
      )}

      {/* ─── Initial Result ─── */}
      {activeResult && (
        <div className="result-block success">
          <strong>STK Push Initiated ✓</strong>
          {'\n'}Transaction ID: {activeResult.transactionId}
          {'\n'}Checkout Request: {activeResult.checkoutRequestId}
          {'\n'}Merchant Request: {activeResult.merchantRequestId}
        </div>
      )}

      {/* ─── Live Polling Status ─── */}
      {liveStatus && (
        <div style={{ marginTop: '1rem' }}>
          <div className="toolbar">
            <h3>Live Transaction Status</h3>
            <span className={`pill ${statusPillClass(liveStatus.status)}`}>
              {liveStatus.status}
            </span>
          </div>

          <table>
            <tbody>
              <tr>
                <td style={{ fontWeight: 600, color: 'var(--color-text-secondary)' }}>Transaction ID</td>
                <td>{liveStatus.transactionId ?? liveStatus.id}</td>
              </tr>
              <tr>
                <td style={{ fontWeight: 600, color: 'var(--color-text-secondary)' }}>Amount</td>
                <td>KES {Number(liveStatus.amount).toLocaleString()}</td>
              </tr>
              <tr>
                <td style={{ fontWeight: 600, color: 'var(--color-text-secondary)' }}>MSISDN</td>
                <td>{liveStatus.msisdn}</td>
              </tr>
              <tr>
                <td style={{ fontWeight: 600, color: 'var(--color-text-secondary)' }}>M-Pesa Receipt</td>
                <td>{liveStatus.mpesaReceiptNumber ?? '—'}</td>
              </tr>
              <tr>
                <td style={{ fontWeight: 600, color: 'var(--color-text-secondary)' }}>Created At</td>
                <td>{liveStatus.createdAt}</td>
              </tr>
            </tbody>
          </table>

          {isResolved && (
            <div style={{ marginTop: '1rem' }}>
              <button
                className="btn btn-secondary btn-sm"
                onClick={() => {
                  setActiveResult(null);
                  setPollingTxId(null);
                  reset();
                }}
              >
                ↻ New Payment
              </button>
            </div>
          )}
        </div>
      )}
    </div>
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
