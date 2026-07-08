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
public class SAPAdapter implements ERPAdapter {

    @Value("${openfloat.erp.sap.base-url}")
    private String baseUrl;

    @Value("${openfloat.erp.sap.client-id}")
    private String clientId;

    @Value("${openfloat.erp.sap.client-secret}")
    private String clientSecret;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public void sendTransaction(TransactionCompletedEvent event) throws Exception {
        log.info("SAP Adapter: Dispatching transaction {} to SAP endpoint: {}", event.getTransactionId(), baseUrl);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-SAP-Client-Id", clientId);
        headers.set("Authorization", "Bearer " + clientSecret); // Placeholder for OAuth2/Token exchange flow

        Map<String, Object> sapPayload = new HashMap<>();
        sapPayload.put("documentDate", event.getCompletedAt().toString());
        sapPayload.put("amount", event.getAmount());
        sapPayload.put("currency", "KES");
        sapPayload.put("accountReference", event.getAccountReference());
        sapPayload.put("mpesaReceipt", event.getReconciliationId() != null ? event.getReconciliationId() : event.getTransactionId());
        sapPayload.put("phone", event.getPhoneNumber());
        sapPayload.put("type", event.getTransactionType());
        sapPayload.put("shortcode", event.getPaybill());

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(sapPayload, headers);
        
        try {
            // In a real environment, we'd make a real POST. For development and robustness, we check connection
            restTemplate.postForObject(baseUrl + "/transactions", requestEntity, String.class);
            log.info("SAP Adapter: Successfully synced transaction: {}", event.getTransactionId());
        } catch (Exception e) {
            log.error("SAP Adapter: Failed to sync transaction: {}. Reason: {}", event.getTransactionId(), e.getMessage());
            // Re-throw so AMQP/Retry listener can capture the failure
            throw e;
        }
    }

    @Override
    public String getSystemName() {
        return "sap";
    }
}
