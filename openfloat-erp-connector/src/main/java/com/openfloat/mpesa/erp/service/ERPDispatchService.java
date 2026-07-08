package com.openfloat.mpesa.erp.service;

import com.openfloat.mpesa.common.event.TransactionCompletedEvent;
import com.openfloat.mpesa.erp.adapter.ERPAdapter;
import com.openfloat.mpesa.erp.entity.ERPSyncRecord;
import com.openfloat.mpesa.erp.repository.ERPSyncRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ERPDispatchService {

    @Value("${openfloat.erp.active-adapter:custom}")
    private String activeAdapterName;

    private final List<ERPAdapter> adapters;
    private final ERPSyncRecordRepository syncRecordRepository;

    /**
     * Dispatches a transaction event to the active ERP adapter.
     * Records the sync attempt, tracking state, and outcome in PostgreSQL.
     */
    @Transactional
    public void dispatch(TransactionCompletedEvent event) throws Exception {
        ERPAdapter activeAdapter = adapters.stream()
                .filter(adapter -> adapter.getSystemName().equalsIgnoreCase(activeAdapterName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No ERP adapter configured matching name: " + activeAdapterName));

        // Look up existing sync record (useful if this is a retry attempt)
        ERPSyncRecord syncRecord = syncRecordRepository.findByTransactionId(event.getTransactionId())
                .orElseGet(() -> {
                    Map<String, Object> payloadMap = new HashMap<>();
                    payloadMap.put("transactionId", event.getTransactionId());
                    payloadMap.put("amount", event.getAmount());
                    payloadMap.put("phoneNumber", event.getPhoneNumber());
                    payloadMap.put("accountReference", event.getAccountReference());
                    payloadMap.put("paybill", event.getPaybill());
                    payloadMap.put("status", event.getStatus());
                    
                    return ERPSyncRecord.builder()
                            .id(UUID.randomUUID())
                            .transactionId(event.getTransactionId())
                            .erpSystem(activeAdapterName)
                            .syncStatus("PENDING")
                            .retryCount(0)
                            .syncPayload(payloadMap)
                            .build();
                });

        syncRecord.setRetryCount(syncRecord.getRetryCount() + 1);

        try {
            log.info("Dispatching transaction {} to active ERP: {} (Attempt {})", 
                    event.getTransactionId(), activeAdapterName, syncRecord.getRetryCount());
            
            // Execute integration dispatch
            activeAdapter.sendTransaction(event);

            // Update sync record on success
            syncRecord.setSyncStatus("SUCCESS");
            syncRecord.setSyncedAt(Instant.now());
            syncRecord.setErrorMessage(null);
            syncRecordRepository.save(syncRecord);
            
            log.info("Successfully completed ERP sync for transaction {}", event.getTransactionId());
        } catch (Exception e) {
            log.error("ERP sync failed for transaction {} (Attempt {}): {}", 
                    event.getTransactionId(), syncRecord.getRetryCount(), e.getMessage());

            // Update sync record on failure
            syncRecord.setSyncStatus("FAILED");
            syncRecord.setErrorMessage(e.getMessage());
            syncRecordRepository.save(syncRecord);

            // Re-throw so caller (Amqp consumer) can coordinate retry policies
            throw e;
        }
    }
}
