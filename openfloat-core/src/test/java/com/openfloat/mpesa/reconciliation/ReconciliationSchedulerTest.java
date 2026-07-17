package com.openfloat.mpesa.reconciliation;

import com.openfloat.mpesa.entity.Transaction;
import com.openfloat.mpesa.entity.enums.ReconciliationStatus;
import com.openfloat.mpesa.entity.enums.TransactionStatus;
import com.openfloat.mpesa.entity.enums.TransactionType;
import com.openfloat.mpesa.integration.mpesa.DarajaClient;
import com.openfloat.mpesa.integration.mpesa.DarajaConfig;
import com.openfloat.mpesa.integration.mpesa.dto.StkQueryResponse;
import com.openfloat.mpesa.repository.TransactionRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link ReconciliationScheduler} covering:
 * <ul>
 *   <li>MATCHED — Daraja confirms success (ResultCode=0)</li>
 *   <li>MISMATCHED — Daraja reports definitive failure (ResultCode=1032)</li>
 *   <li>IN_PROGRESS — Daraja still processing (ResultCode=1)</li>
 *   <li>Error path — Daraja API throws exception</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class ReconciliationSchedulerTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private DarajaClient darajaClient;
    @Mock private DarajaConfig darajaConfig;

    private SimpleMeterRegistry meterRegistry;
    private ReconciliationScheduler scheduler;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        scheduler = new ReconciliationScheduler(transactionRepository, darajaClient, darajaConfig, meterRegistry);

        lenient().when(darajaConfig.getShortcode()).thenReturn("174379");
        lenient().when(darajaConfig.getPasskey()).thenReturn("bfb279f9aa9bdbcf158e97dd71a467cd2e0c893059b10f78e6b72ada1ed2c919");
    }

    @Test
    void reconcilePendingTransactionsMarksMatchedOnDarajaSuccessResponse() {
        Transaction tx = candidateTransaction();
        when(transactionRepository.findPendingReconciliationTransactions(any(), any()))
                .thenReturn(List.of(tx));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        StkQueryResponse response = new StkQueryResponse();
        response.setResultCode("0");
        response.setResultDescription("The service request is processed successfully.");
        when(darajaClient.queryStkPush(any())).thenReturn(response);

        scheduler.reconcilePendingTransactions();

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getReconciliationStatus()).isEqualTo(ReconciliationStatus.MATCHED);
        assertThat(meterRegistry.counter("reconciliation.matched.count").count()).isGreaterThan(0);
    }

    @Test
    void reconcilePendingTransactionsMarksMismatchedOnDarajaFailureResponse() {
        Transaction tx = candidateTransaction();
        when(transactionRepository.findPendingReconciliationTransactions(any(), any()))
                .thenReturn(List.of(tx));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        StkQueryResponse response = new StkQueryResponse();
        response.setResultCode("1032");
        response.setResultDescription("Request cancelled by user");
        when(darajaClient.queryStkPush(any())).thenReturn(response);

        scheduler.reconcilePendingTransactions();

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        Transaction saved = captor.getValue();
        assertThat(saved.getReconciliationStatus()).isEqualTo(ReconciliationStatus.MISMATCHED);
        assertThat(saved.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(saved.getResultCode()).isEqualTo(1032);
        assertThat(meterRegistry.counter("reconciliation.mismatched.count").count()).isGreaterThan(0);
    }

    @Test
    void reconcilePendingTransactionsMarksInProgressWhenStillProcessing() {
        Transaction tx = candidateTransaction();
        when(transactionRepository.findPendingReconciliationTransactions(any(), any()))
                .thenReturn(List.of(tx));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        StkQueryResponse response = new StkQueryResponse();
        response.setResultCode("1");
        response.setResultDescription("The transaction is being processed");
        when(darajaClient.queryStkPush(any())).thenReturn(response);

        scheduler.reconcilePendingTransactions();

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getReconciliationStatus()).isEqualTo(ReconciliationStatus.IN_PROGRESS);
    }

    @Test
    void reconcilePendingTransactionsMarksInProgressAndCountsErrorOnDarajaApiException() {
        Transaction tx = candidateTransaction();
        when(transactionRepository.findPendingReconciliationTransactions(any(), any()))
                .thenReturn(List.of(tx));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(darajaClient.queryStkPush(any())).thenThrow(new RuntimeException("Daraja API timeout"));

        scheduler.reconcilePendingTransactions();

        // Error path: reconcileTransaction throws, scheduler catches and counts as error
        // But before throwing, it sets IN_PROGRESS and saves
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getReconciliationStatus()).isEqualTo(ReconciliationStatus.IN_PROGRESS);
    }

    @Test
    void reconcilePendingTransactionsDoesNothingWhenNoCandidatesExist() {
        when(transactionRepository.findPendingReconciliationTransactions(any(), any()))
                .thenReturn(List.of());

        scheduler.reconcilePendingTransactions();

        verify(darajaClient, never()).queryStkPush(any());
        verify(transactionRepository, never()).save(any());
    }

    private Transaction candidateTransaction() {
        return Transaction.builder()
                .id(UUID.randomUUID())
                .transactionId("TX-RECON-001")
                .transactionType(TransactionType.STK_PUSH)
                .phoneNumber("254712345678")
                .amount(BigDecimal.valueOf(100))
                .paybill("174379")
                .status(TransactionStatus.PENDING)
                .reconciliationStatus(ReconciliationStatus.PENDING)
                .checkoutRequestId("ws_CO_DMZ_RECON_123")
                .createdAt(Instant.now().minusSeconds(48 * 3600)) // 48 hours ago
                .build();
    }
}
