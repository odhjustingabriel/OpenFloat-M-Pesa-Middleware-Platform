package com.openfloat.mpesa.erp.adapter;

import com.openfloat.mpesa.common.event.TransactionCompletedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class DynamicsAdapter implements ERPAdapter {

    @Value("${openfloat.erp.dynamics.base-url}")
    private String baseUrl;

    @Value("${openfloat.erp.dynamics.tenant-id}")
    private String tenantId;

    @Value("${openfloat.erp.dynamics.client-id}")
    private String clientId;

    @Value("${openfloat.erp.dynamics.client-secret}")
    private String clientSecret;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public void sendTransaction(TransactionCompletedEvent event) throws Exception {
        log.info("Dynamics 365 Adapter: Dispatching transaction {} for tenant: {}", event.getTransactionId(), tenantId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Dynamics-Client-Id", clientId);
        headers.set("X-Dynamics-Secret", clientSecret);

        Map<String, Object> dynamicsPayload = new HashMap<>();
        dynamicsPayload.put("journalBatchName", "MPESA_PYMT");
        dynamicsPayload.put("lineNo", System.currentTimeMillis());
        dynamicsPayload.put("documentNo", event.getReconciliationId() != null ? event.getReconciliationId() : event.getTransactionId());
        dynamicsPayload.put("accountType", "Customer");
        dynamicsPayload.put("accountNo", event.getAccountReference());
        dynamicsPayload.put("amount", event.getAmount());
        dynamicsPayload.put("description", "M-Pesa payment - " + event.getPhoneNumber());

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(dynamicsPayload, headers);
        
        try {
            restTemplate.postForObject(baseUrl + "/general-journals", requestEntity, String.class);
            log.info("Dynamics 365 Adapter: Successfully synced transaction: {}", event.getTransactionId());
        } catch (Exception e) {
            log.error("Dynamics 365 Adapter: Failed to sync transaction: {}. Reason: {}", event.getTransactionId(), e.getMessage());
            throw e;
        }
    }

    @Override
    public String getSystemName() {
        return "dynamics";
    }
}
