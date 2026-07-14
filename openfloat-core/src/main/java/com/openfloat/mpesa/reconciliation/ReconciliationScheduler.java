package com.openfloat.mpesa.reconciliation;

import com.openfloat.mpesa.entity.Transaction;
import com.openfloat.mpesa.entity.enums.ReconciliationStatus;
import com.openfloat.mpesa.entity.enums.TransactionStatus;
import com.openfloat.mpesa.integration.mpesa.DarajaClient;
import com.openfloat.mpesa.integration.mpesa.DarajaConfig;
import com.openfloat.mpesa.integration.mpesa.dto.StkQueryRequest;
import com.openfloat.mpesa.integration.mpesa.dto.StkQueryResponse;
import com.openfloat.mpesa.repository.TransactionRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;

/**
 * Nightly reconciliation job that cross-references PENDING STK Push transactions
 * against the Safaricom Daraja STK Query API and updates their status accordingly.
 *
 * <p>Schedule: Every night at 02:00 UTC ({@code "0 0 2 * * ?"}).
 *
 * <p>Criteria for reconciliation candidates:
 * <ul>
 *   <li>{@code reconciliationStatus = PENDING}</li>
 *   <li>{@code createdAt < NOW() - 24 hours} — gives callbacks ample time to arrive first</li>
 *   <li>{@code checkoutRequestId IS NOT NULL} — only STK Push transactions are queryable</li>
 * </ul>
 *
 * <p>For each candidate, the scheduler calls the Daraja STK Query endpoint.
 * Based on the response:
 * <ul>
 *   <li>Success ({@code ResultCode = 0}) → sets reconciliation status to {@code MATCHED}</li>
 *   <li>Failure (definitive error code) → marks transaction status {@code FAILED} +
 *       reconciliation status {@code MISMATCHED}</li>
 *   <li>Still processing or API error → marks reconciliation status {@code IN_PROGRESS}
 *       so the next nightly run will retry</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReconciliationScheduler {

    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** Transactions older than this many hours are eligible for reconciliation. */
    private static final long RECONCILIATION_CUTOFF_HOURS = 24L;

    private final TransactionRepository transactionRepository;
    private final DarajaClient          darajaClient;
    private final DarajaConfig          darajaConfig;
    private final MeterRegistry         meterRegistry;

    /**
     * Runs every night at 02:00 UTC.
     * Processes all STK Push transactions that are still pending reconciliation
     * and were created more than 24 hours ago.
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void reconcilePendingTransactions() {
        log.info("RECONCILIATION — Starting nightly reconciliation job at {}", Instant.now());

        Instant cutoff = Instant.now().minusSeconds(RECONCILIATION_CUTOFF_HOURS * 3600L);

        List<Transaction> candidates = transactionRepository
                .findPendingReconciliationTransactions(ReconciliationStatus.PENDING, cutoff);

        log.info("RECONCILIATION — Found {} candidate transactions to reconcile (cutoff={})",
                candidates.size(), cutoff);

        int matched    = 0;
        int mismatched = 0;
        int inProgress = 0;
        int errors     = 0;

        for (Transaction transaction : candidates) {
            try {
                ReconciliationOutcome outcome = reconcileTransaction(transaction);
                switch (outcome) {
                    case MATCHED    -> {
                        matched++;
                        incrementCounter("reconciliation.matched.count");
                    }
                    case MISMATCHED -> {
                        mismatched++;
                        incrementCounter("reconciliation.mismatched.count");
                    }
                    case IN_PROGRESS -> inProgress++;
                }
            } catch (Exception e) {
                errors++;
                log.error("RECONCILIATION — Unexpected error processing txnId={}. Error: {}",
                        transaction.getTransactionId(), e.getMessage(), e);
            }
        }

        log.info("RECONCILIATION — Completed. total={} matched={} mismatched={} inProgress={} errors={}",
                candidates.size(), matched, mismatched, inProgress, errors);
    }

    private void incrementCounter(String metricName) {
        Counter.builder(metricName)
                .description("Nightly reconciliation outcome counter")
                .register(meterRegistry)
                .increment();
    }

    // ── Private helpers ───────────────────────────────────────────────────

    /**
     * Queries Daraja for the given transaction and updates its status in the database.
     *
     * @return the reconciliation outcome for this transaction
     */
    @Transactional
    protected ReconciliationOutcome reconcileTransaction(Transaction transaction) {
        String checkoutRequestId = transaction.getCheckoutRequestId();
        String transactionId     = transaction.getTransactionId();

        log.debug("RECONCILIATION — Querying Daraja for txnId={}, checkoutRequestId={}",
                transactionId, checkoutRequestId);

        try {
            StkQueryRequest request = buildStkQueryRequest(checkoutRequestId);
            StkQueryResponse response = darajaClient.queryStkPush(request);

            if (response.isTransactionSuccessful()) {
                log.info("RECONCILIATION — txnId={} confirmed SUCCESS by Daraja. Marking MATCHED.",
                        transactionId);
                transaction.setReconciliationStatus(ReconciliationStatus.MATCHED);
                // Ensure the transaction status reflects success if it was still PENDING
                if (TransactionStatus.PENDING.equals(transaction.getStatus())) {
                    transaction.setStatus(TransactionStatus.SUCCESS);
                    transaction.setResultCode(0);
                    transaction.setResultDescription(response.getResultDescription());
                }
                transactionRepository.save(transaction);
                return ReconciliationOutcome.MATCHED;

            } else if (response.isTransactionFailed()) {
                log.warn("RECONCILIATION — txnId={} confirmed FAILED by Daraja. ResultCode={}, Desc={}. " +
                         "Marking MISMATCHED.",
                        transactionId, response.getResultCode(), response.getResultDescription());
                transaction.setStatus(TransactionStatus.FAILED);
                transaction.setReconciliationStatus(ReconciliationStatus.MISMATCHED);
                transaction.setResultCode(response.getResultCode() != null
                        ? Integer.parseInt(response.getResultCode()) : null);
                transaction.setResultDescription(response.getResultDescription());
                transactionRepository.save(transaction);
                return ReconciliationOutcome.MISMATCHED;

            } else {
                // ResultCode = 1 or still processing — will be retried next run
                log.info("RECONCILIATION — txnId={} still processing at Daraja (ResultCode={}). " +
                         "Marking IN_PROGRESS for retry.",
                        transactionId, response.getResultCode());
                transaction.setReconciliationStatus(ReconciliationStatus.IN_PROGRESS);
                transactionRepository.save(transaction);
                return ReconciliationOutcome.IN_PROGRESS;
            }

        } catch (Exception e) {
            log.error("RECONCILIATION — Daraja query FAILED for txnId={}. Error: {}. " +
                      "Marking IN_PROGRESS for retry on next run.",
                    transactionId, e.getMessage());
            transaction.setReconciliationStatus(ReconciliationStatus.IN_PROGRESS);
            transactionRepository.save(transaction);
            throw e; // let caller count this as an error
        }
    }

    /**
     * Builds a Daraja STK Push Query request with the LNMO password
     * (Base64 of ShortCode + Passkey + Timestamp).
     */
    private StkQueryRequest buildStkQueryRequest(String checkoutRequestId) {
        String timestamp = ZonedDateTime.now(ZoneOffset.UTC).format(TIMESTAMP_FMT);
        String rawPassword = darajaConfig.getShortcode()
                + darajaConfig.getPasskey()
                + timestamp;
        String password = Base64.getEncoder()
                .encodeToString(rawPassword.getBytes(StandardCharsets.UTF_8));

        return StkQueryRequest.builder()
                .businessShortCode(darajaConfig.getShortcode())
                .password(password)
                .timestamp(timestamp)
                .checkoutRequestId(checkoutRequestId)
                .build();
    }

    /** Internal enum for tracking outcomes within a single scheduler run. */
    private enum ReconciliationOutcome {
        MATCHED, MISMATCHED, IN_PROGRESS
    }
}
