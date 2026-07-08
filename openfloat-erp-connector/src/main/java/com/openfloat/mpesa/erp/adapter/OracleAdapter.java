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
public class OracleAdapter implements ERPAdapter {

    @Value("${openfloat.erp.oracle.base-url}")
    private String baseUrl;

    @Value("${openfloat.erp.oracle.api-key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public void sendTransaction(TransactionCompletedEvent event) throws Exception {
        log.info("Oracle Adapter: Dispatching transaction {} to Oracle Financials: {}", event.getTransactionId(), baseUrl);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "ApiKey " + apiKey);

        Map<String, Object> oraclePayload = new HashMap<>();
        oraclePayload.put("ledgerName", "KES_MAIN_LEDGER");
        oraclePayload.put("source", "M-PESA");
        oraclePayload.put("enteredDr", event.getAmount());
        oraclePayload.put("referenceText", event.getAccountReference());
        oraclePayload.put("receiptNumber", event.getReconciliationId());
        oraclePayload.put("phone", event.getPhoneNumber());
        oraclePayload.put("transactionType", event.getTransactionType());
        oraclePayload.put("timestamp", event.getCompletedAt().toString());

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(oraclePayload, headers);
        
        try {
            restTemplate.postForObject(baseUrl + "/journal-entries", requestEntity, String.class);
            log.info("Oracle Adapter: Successfully synced transaction: {}", event.getTransactionId());
        } catch (Exception e) {
            log.error("Oracle Adapter: Failed to sync transaction: {}. Reason: {}", event.getTransactionId(), e.getMessage());
            throw e;
        }
    }

    @Override
    public String getSystemName() {
        return "oracle";
    }
}
