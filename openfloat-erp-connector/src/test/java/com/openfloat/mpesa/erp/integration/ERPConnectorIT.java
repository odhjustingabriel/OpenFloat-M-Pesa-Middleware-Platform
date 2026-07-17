package com.openfloat.mpesa.erp.integration;

import com.openfloat.mpesa.common.event.TransactionCompletedEvent;
import com.openfloat.mpesa.erp.adapter.CustomAdapter;
import com.openfloat.mpesa.erp.entity.ERPSyncRecord;
import com.openfloat.mpesa.erp.repository.ERPSyncRecordRepository;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class ERPConnectorIT extends BaseErpIntegrationTest {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ERPSyncRecordRepository repository;

    @MockBean
    private CustomAdapter customAdapter;

    @Test
    void testSuccessfulErpSync() throws Exception {
        when(customAdapter.getSystemName()).thenReturn("custom");
        doNothing().when(customAdapter).sendTransaction(any());

        TransactionCompletedEvent event = TransactionCompletedEvent.builder()
                .transactionId("TX-ERP-001")
                .transactionType("STK_PUSH")
                .amount(BigDecimal.TEN)
                .phoneNumber("254712345678")
                .accountReference("INV-001")
                .paybill("174379")
                .status("SUCCESS")
                .reconciliationId("REC-001")
                .completedAt(Instant.now())
                .build();

        rabbitTemplate.convertAndSend("exchange.transaction.completed", "transaction.completed", event);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<ERPSyncRecord> recordOpt = repository.findByTransactionId("TX-ERP-001");
            assertThat(recordOpt).isPresent();
            assertThat(recordOpt.get().getSyncStatus()).isEqualTo("SUCCESS");
            assertThat(recordOpt.get().getRetryCount()).isEqualTo(1);
        });
    }

    @Test
    void testErpSyncFailuresGoToDlq() throws Exception {
        when(customAdapter.getSystemName()).thenReturn("custom");
        doThrow(new RuntimeException("ERP unavailable")).when(customAdapter).sendTransaction(any());

        TransactionCompletedEvent event = TransactionCompletedEvent.builder()
                .transactionId("TX-ERP-002")
                .transactionType("STK_PUSH")
                .amount(BigDecimal.TEN)
                .phoneNumber("254712345678")
                .accountReference("INV-002")
                .paybill("174379")
                .status("SUCCESS")
                .reconciliationId("REC-002")
                .completedAt(Instant.now())
                .build();

        // Set test retry count configuration to fail fast
        rabbitTemplate.convertAndSend("exchange.transaction.completed", "transaction.completed", event);

        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<ERPSyncRecord> recordOpt = repository.findByTransactionId("TX-ERP-002");
            assertThat(recordOpt).isPresent();
            assertThat(recordOpt.get().getSyncStatus()).isEqualTo("FAILED");
            assertThat(recordOpt.get().getRetryCount()).isGreaterThanOrEqualTo(1);
        });
    }
}
