package com.openfloat.mpesa.service;

import com.openfloat.mpesa.dto.StkPushRequestDto;
import com.openfloat.mpesa.entity.Transaction;
import com.openfloat.mpesa.entity.enums.TransactionStatus;
import com.openfloat.mpesa.integration.mpesa.DarajaClient;
import com.openfloat.mpesa.integration.mpesa.DarajaConfig;
import com.openfloat.mpesa.integration.mpesa.dto.StkPushResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StkPushServiceTest {

    @Mock private DarajaClient darajaClient;
    @Mock private DarajaConfig darajaConfig;
    @Mock private TransactionService transactionService;
    @Mock private IdempotencyService idempotencyService;

    private StkPushService service;

    @BeforeEach
    void setUp() {
        service = new StkPushService(darajaClient, darajaConfig, transactionService, idempotencyService, new SimpleMeterRegistry());
        lenient().when(darajaConfig.getShortcode()).thenReturn("174379");
        lenient().when(darajaConfig.getPasskey()).thenReturn("passkey");
        lenient().when(darajaConfig.getCallbackBaseUrl()).thenReturn("https://callbacks.example.com");
        lenient().when(idempotencyService.generateIdempotencyKey(any(), any(), any(), any())).thenReturn("idem-key");
        lenient().when(transactionService.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction tx = invocation.getArgument(0);
            if (tx.getId() == null) {
                tx.setId(UUID.randomUUID());
            }
            return tx;
        });
    }

    @Test
    void initiateStkPushPersistsPendingTransactionThenReturnsDarajaIdentifiersOnHappyPath() {
        when(darajaClient.initiateStkPush(any())).thenReturn(StkPushResponse.builder()
                .merchantRequestId("merchant-1")
                .checkoutRequestId("checkout-1")
                .responseCode("0")
                .responseDescription("Accepted")
                .build());

        var response = service.initiateStkPush(request());

        assertThat(response.getStatus()).isEqualTo(TransactionStatus.PENDING.name());
        verify(idempotencyService).checkIdempotency("idem-key");
        verify(idempotencyService).saveIdempotencyKey("idem-key");
        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionService, atLeast(2)).save(txCaptor.capture());
        Transaction saved = txCaptor.getAllValues().get(txCaptor.getAllValues().size() - 1);
        assertThat(saved.getCheckoutRequestId()).isEqualTo("checkout-1");
        assertThat(saved.getMerchantRequestId()).isEqualTo("merchant-1");
    }

    @Test
    void initiateStkPushMarksTransactionFailedWhenDarajaRejectsRequest() {
        when(darajaClient.initiateStkPush(any())).thenReturn(StkPushResponse.builder()
                .responseCode("1032")
                .responseDescription("Request cancelled")
                .build());

        var response = service.initiateStkPush(request());

        assertThat(response.getStatus()).isEqualTo(TransactionStatus.FAILED.name());
    }

    @Test
    void initiateStkPushPropagatesDuplicateIdempotencyBeforePersisting() {
        doThrow(new IllegalStateException("duplicate")).when(idempotencyService).checkIdempotency("idem-key");

        assertThatThrownBy(() -> service.initiateStkPush(request()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate");
        verifyNoInteractions(darajaClient, transactionService);
    }

    private StkPushRequestDto request() {
        return StkPushRequestDto.builder()
                .msisdn("0712345678")
                .amount(BigDecimal.TEN)
                .paybill("174379")
                .accountRef("INV-1")
                .description("Test payment")
                .build();
    }
}
