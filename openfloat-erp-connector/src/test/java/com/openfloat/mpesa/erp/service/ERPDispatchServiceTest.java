package com.openfloat.mpesa.erp.service;

import com.openfloat.mpesa.common.event.TransactionCompletedEvent;
import com.openfloat.mpesa.erp.adapter.ERPAdapter;
import com.openfloat.mpesa.erp.entity.ERPSyncRecord;
import com.openfloat.mpesa.erp.repository.ERPSyncRecordRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class ERPDispatchServiceTest {

    @Mock private ERPAdapter customAdapter;
    @Mock private ERPAdapter sapAdapter;
    @Mock private ERPSyncRecordRepository repository;

    private ERPDispatchService service;

    @BeforeEach
    void setUp() {
        when(customAdapter.getSystemName()).thenReturn("custom");
        lenient().when(sapAdapter.getSystemName()).thenReturn("sap");
        service = new ERPDispatchService(List.of(customAdapter, sapAdapter), repository, new SimpleMeterRegistry());
        ReflectionTestUtils.setField(service, "activeAdapterName", "custom");
    }

    @Test
    void dispatchRoutesToActiveAdapterAndMarksRecordSuccessful() throws Exception {
        when(repository.findByTransactionId("TX-1")).thenReturn(Optional.empty());

        service.dispatch(event());

        verify(customAdapter).sendTransaction(event());
        verify(sapAdapter, never()).sendTransaction(event());
        ArgumentCaptor<ERPSyncRecord> captor = ArgumentCaptor.forClass(ERPSyncRecord.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getSyncStatus()).isEqualTo("SUCCESS");
        assertThat(captor.getValue().getRetryCount()).isEqualTo(1);
    }

    @Test
    void dispatchIncrementsRetryCountAndPersistsFailureOnAdapterError() throws Exception {
        ERPSyncRecord existing = ERPSyncRecord.builder()
                .transactionId("TX-1")
                .erpSystem("custom")
                .syncStatus("FAILED")
                .retryCount(2)
                .build();
        when(repository.findByTransactionId("TX-1")).thenReturn(Optional.of(existing));
        doThrow(new RuntimeException("ERP unavailable")).when(customAdapter).sendTransaction(event());

        assertThatThrownBy(() -> service.dispatch(event()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("ERP unavailable");

        ArgumentCaptor<ERPSyncRecord> captor = ArgumentCaptor.forClass(ERPSyncRecord.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getSyncStatus()).isEqualTo("FAILED");
        assertThat(captor.getValue().getRetryCount()).isEqualTo(3);
        assertThat(captor.getValue().getErrorMessage()).isEqualTo("ERP unavailable");
    }

    private TransactionCompletedEvent event() {
        return TransactionCompletedEvent.builder()
                .transactionId("TX-1")
                .amount(BigDecimal.TEN)
                .phoneNumber("254712345678")
                .accountReference("INV-1")
                .paybill("174379")
                .status("SUCCESS")
                .build();
    }
}
