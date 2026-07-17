package com.openfloat.mpesa.service;

import com.openfloat.mpesa.common.event.TransactionCompletedEvent;
import com.openfloat.mpesa.entity.Callback;
import com.openfloat.mpesa.entity.Transaction;
import com.openfloat.mpesa.entity.enums.TransactionStatus;
import com.openfloat.mpesa.entity.enums.TransactionType;
import com.openfloat.mpesa.event.TransactionEventPublisher;
import com.openfloat.mpesa.mapper.CallbackMapper;
import com.openfloat.mpesa.mapper.TransactionMapper;
import com.openfloat.mpesa.repository.CallbackRepository;
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
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class CallbackServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private CallbackRepository callbackRepository;
    @Mock private TransactionEventPublisher eventPublisher;
    @Mock private TransactionMapper transactionMapper;
    @Mock private CallbackMapper callbackMapper;

    private CallbackService service;

    @BeforeEach
    void setUp() {
        service = new CallbackService(
                transactionRepository, callbackRepository, eventPublisher,
                transactionMapper, callbackMapper, new SimpleMeterRegistry()
        );

        lenient().when(callbackMapper.toEntity(any(), any(), any(), any()))
                .thenReturn(Callback.builder().id(UUID.randomUUID()).build());
        lenient().when(callbackRepository.save(any(Callback.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(transactionMapper.toEvent(any(Transaction.class)))
                .thenReturn(TransactionCompletedEvent.builder()
                        .transactionId("TX-1")
                        .amount(BigDecimal.TEN)
                        .phoneNumber("254712345678")
                        .status("SUCCESS")
                        .build());
    }

    // ── STK Callback Tests ────────────────────────────────────────────────

    @Test
    void processStkCallbackSetsSuccessAndPublishesEventOnResultCodeZero() {
        Transaction tx = pendingStkTransaction();
        when(transactionRepository.findByCheckoutRequestId("ws_CO_DMZ_123")).thenReturn(Optional.of(tx));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        service.processStkCallback(stkSuccessPayload());

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(captor.getValue().getResultCode()).isEqualTo(0);
        assertThat(captor.getValue().getReconciliationId()).startsWith("REC-");
        verify(eventPublisher, times(1)).publish(any(TransactionCompletedEvent.class));
    }

    @Test
    void processStkCallbackSetsFailedAndPublishesEventOnNonZeroResultCode() {
        Transaction tx = pendingStkTransaction();
        when(transactionRepository.findByCheckoutRequestId("ws_CO_DMZ_123")).thenReturn(Optional.of(tx));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        service.processStkCallback(stkFailurePayload());

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(captor.getValue().getResultCode()).isEqualTo(1032);
        verify(eventPublisher, times(1)).publish(any(TransactionCompletedEvent.class));
    }

    @Test
    void processStkCallbackSkipsSilentlyWhenTransactionAlreadyProcessed() {
        Transaction tx = pendingStkTransaction();
        tx.setStatus(TransactionStatus.SUCCESS);
        when(transactionRepository.findByCheckoutRequestId("ws_CO_DMZ_123")).thenReturn(Optional.of(tx));

        service.processStkCallback(stkSuccessPayload());

        verify(transactionRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void processStkCallbackSkipsSilentlyWhenTransactionNotFound() {
        when(transactionRepository.findByCheckoutRequestId("ws_CO_DMZ_123")).thenReturn(Optional.empty());

        service.processStkCallback(stkSuccessPayload());

        verify(transactionRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    // ── B2C Callback Tests ────────────────────────────────────────────────

    @Test
    void processB2cCallbackSetsSuccessAndPublishesEventOnResultCodeZero() {
        Transaction tx = pendingB2cTransaction();
        when(transactionRepository.findByConversationId("AG_20260101_0001")).thenReturn(Optional.of(tx));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        service.processB2cCallback(b2cSuccessPayload());

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(captor.getValue().getReconciliationId()).startsWith("REC-");
        verify(eventPublisher, times(1)).publish(any(TransactionCompletedEvent.class));
    }

    @Test
    void processB2cCallbackSetsFailedOnNonZeroResultCode() {
        Transaction tx = pendingB2cTransaction();
        when(transactionRepository.findByConversationId("AG_20260101_0001")).thenReturn(Optional.of(tx));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        service.processB2cCallback(b2cFailurePayload());

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(TransactionStatus.FAILED);
    }

    // ── Reversal Callback Tests ───────────────────────────────────────────

    @Test
    void processReversalCallbackSetsSuccessAndPublishesEventOnResultCodeZero() {
        Transaction tx = pendingReversalTransaction();
        when(transactionRepository.findByConversationId("AG_REVERSAL_001")).thenReturn(Optional.of(tx));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        service.processReversalCallback(reversalSuccessPayload());

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(captor.getValue().getReconciliationId()).startsWith("REC-");
        verify(eventPublisher, times(1)).publish(any(TransactionCompletedEvent.class));
    }

    // ── Payload Builders ──────────────────────────────────────────────────

    private Transaction pendingStkTransaction() {
        return Transaction.builder()
                .id(UUID.randomUUID())
                .transactionType(TransactionType.STK_PUSH)
                .phoneNumber("254712345678")
                .amount(BigDecimal.TEN)
                .paybill("174379")
                .status(TransactionStatus.PENDING)
                .checkoutRequestId("ws_CO_DMZ_123")
                .createdAt(Instant.now())
                .build();
    }

    private Transaction pendingB2cTransaction() {
        return Transaction.builder()
                .id(UUID.randomUUID())
                .transactionType(TransactionType.B2C)
                .phoneNumber("254712345678")
                .amount(new BigDecimal("5000"))
                .paybill("174379")
                .status(TransactionStatus.PENDING)
                .conversationId("AG_20260101_0001")
                .createdAt(Instant.now())
                .build();
    }

    private Transaction pendingReversalTransaction() {
        return Transaction.builder()
                .id(UUID.randomUUID())
                .transactionType(TransactionType.REVERSAL)
                .phoneNumber("254712345678")
                .amount(BigDecimal.TEN)
                .paybill("174379")
                .status(TransactionStatus.PENDING)
                .conversationId("AG_REVERSAL_001")
                .createdAt(Instant.now())
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> stkSuccessPayload() {
        Map<String, Object> item1 = Map.of("Name", "MpesaReceiptNumber", "Value", "RCPT123456");
        Map<String, Object> item2 = Map.of("Name", "Amount", "Value", 10);
        Map<String, Object> metadata = Map.of("Item", List.of(item1, item2));

        Map<String, Object> stkCallback = new LinkedHashMap<>();
        stkCallback.put("MerchantRequestID", "MR-001");
        stkCallback.put("CheckoutRequestID", "ws_CO_DMZ_123");
        stkCallback.put("ResultCode", 0);
        stkCallback.put("ResultDesc", "The service request is processed successfully.");
        stkCallback.put("CallbackMetadata", metadata);

        return Map.of("Body", Map.of("stkCallback", stkCallback));
    }

    private Map<String, Object> stkFailurePayload() {
        Map<String, Object> stkCallback = new LinkedHashMap<>();
        stkCallback.put("MerchantRequestID", "MR-001");
        stkCallback.put("CheckoutRequestID", "ws_CO_DMZ_123");
        stkCallback.put("ResultCode", 1032);
        stkCallback.put("ResultDesc", "Request cancelled by user");

        return Map.of("Body", Map.of("stkCallback", stkCallback));
    }

    private Map<String, Object> b2cSuccessPayload() {
        Map<String, Object> param1 = Map.of("Key", "TransactionID", "Value", "B2C_TXN_001");
        Map<String, Object> resultParameters = Map.of("ResultParameter", List.of(param1));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ConversationID", "AG_20260101_0001");
        result.put("OriginatorConversationID", "ORIG-001");
        result.put("ResultCode", 0);
        result.put("ResultDesc", "The service request is processed successfully.");
        result.put("ResultParameters", resultParameters);

        return Map.of("Result", result);
    }

    private Map<String, Object> b2cFailurePayload() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ConversationID", "AG_20260101_0001");
        result.put("OriginatorConversationID", "ORIG-001");
        result.put("ResultCode", 2001);
        result.put("ResultDesc", "The initiator information is invalid.");

        return Map.of("Result", result);
    }

    private Map<String, Object> reversalSuccessPayload() {
        Map<String, Object> param1 = Map.of("Key", "TransactionID", "Value", "REV_TXN_001");
        Map<String, Object> resultParameters = Map.of("ResultParameter", List.of(param1));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ConversationID", "AG_REVERSAL_001");
        result.put("ResultCode", 0);
        result.put("ResultDesc", "Reversal processed successfully.");
        result.put("ResultParameters", resultParameters);

        return Map.of("Result", result);
    }
}
