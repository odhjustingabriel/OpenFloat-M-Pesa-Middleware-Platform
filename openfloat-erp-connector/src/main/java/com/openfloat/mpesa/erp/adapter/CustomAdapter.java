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
public class CustomAdapter implements ERPAdapter {

    @Value("${openfloat.erp.custom.base-url}")
    private String baseUrl;

    @Value("${openfloat.erp.custom.api-key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public void sendTransaction(TransactionCompletedEvent event) throws Exception {
        log.info("Custom REST Adapter: Dispatching transaction {} to custom endpoint: {}", event.getTransactionId(), baseUrl);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-API-Key", apiKey);

        Map<String, Object> payload = new HashMap<>();
        payload.put("transactionId", event.getTransactionId());
        payload.put("type", event.getTransactionType());
        payload.put("amount", event.getAmount());
        payload.put("phoneNumber", event.getPhoneNumber());
        payload.put("accountReference", event.getAccountReference());
        payload.put("paybill", event.getPaybill());
        payload.put("status", event.getStatus());
        payload.put("resultCode", event.getResultCode());
        payload.put("resultDescription", event.getResultDescription());
        payload.put("reconciliationId", event.getReconciliationId());
        payload.put("completedAt", event.getCompletedAt().toString());

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(payload, headers);
        
        try {
            restTemplate.postForObject(baseUrl + "/transactions", requestEntity, String.class);
            log.info("Custom REST Adapter: Successfully synced transaction: {}", event.getTransactionId());
        } catch (Exception e) {
            log.error("Custom REST Adapter: Failed to sync transaction: {}. Reason: {}", event.getTransactionId(), e.getMessage());
            throw e;
        }
    }

    @Override
    public String getSystemName() {
        return "custom";
    }
}
