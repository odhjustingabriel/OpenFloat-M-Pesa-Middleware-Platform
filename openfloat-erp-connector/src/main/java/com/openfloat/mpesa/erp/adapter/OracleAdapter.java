package com.openfloat.mpesa.erp.adapter;

import com.openfloat.mpesa.common.event.TransactionCompletedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ERP adapter for Oracle Fusion Financials / Oracle ERP Cloud via REST.
 *
 * <p>Authentication: HTTP Basic Auth (Base64-encoded username:password).
 * Oracle ERP Cloud REST APIs use basic authentication with an integration user
 * account. For enhanced security, consider switching to OAuth2 in production
 * by providing the token endpoint via {@code openfloat.erp.oracle.token-url}.
 *
 * <p>Payload: Mapped to an Oracle General Ledger journal import request.
 * The {@code ledgerName} and {@code source} fields must match values
 * configured in Oracle General Ledger lookup sets.
 */
@Slf4j
@Component
public class OracleAdapter implements ERPAdapter {

    @Value("${openfloat.erp.oracle.base-url}")
    private String baseUrl;

    /** Oracle integration user — must have journal entry import privileges. */
    @Value("${openfloat.erp.oracle.username:oracle-integration-user}")
    private String username;

    @Value("${openfloat.erp.oracle.api-key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public void sendTransaction(TransactionCompletedEvent event) throws Exception {
        log.info("Oracle Adapter: Dispatching txnId={} to Oracle Financials: {}",
                event.getTransactionId(), baseUrl);

        HttpHeaders headers = buildHeaders();
        Map<String, Object> payload = buildPayload(event);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

        try {
            String response = restTemplate.postForObject(
                    baseUrl + "/journal-entries",
                    request,
                    String.class);
            log.info("Oracle Adapter: Sync successful for txnId={}. Oracle response: {}",
                    event.getTransactionId(), response);
        } catch (Exception e) {
            log.error("Oracle Adapter: Sync FAILED for txnId={}. Error: {}",
                    event.getTransactionId(), e.getMessage());
            throw e;
        }
    }

    @Override
    public String getSystemName() {
        return "oracle";
    }

    // ── Private helpers ───────────────────────────────────────────────────

    /**
     * Oracle ERP Cloud REST API supports both Basic Auth and OAuth2.
     * Using Basic Auth here (username:api-key as password) per common
     * integration patterns. Switch to OAuth2 by configuring a token endpoint.
     */
    private HttpHeaders buildHeaders() {
        String credentials = username + ":" + apiKey;
        String encoded = Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.AUTHORIZATION, "Basic " + encoded);
        return headers;
    }

    /**
     * Maps a completed M-Pesa transaction to an Oracle GL journal import payload.
     * Field names align with Oracle Financials REST API v11.13+ journal entry schema.
     */
    private Map<String, Object> buildPayload(TransactionCompletedEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ledgerName",       "KES_MAIN_LEDGER");
        payload.put("source",           "M-PESA");
        payload.put("category",         "MISCELLANEOUS");
        payload.put("currency",         "KES");
        payload.put("enteredDr",        event.getAmount());
        payload.put("referenceText",    event.getAccountReference());
        payload.put("receiptNumber",    event.getReconciliationId());
        payload.put("msisdn",           event.getPhoneNumber());
        payload.put("transactionType",  event.getTransactionType());
        payload.put("paybill",          event.getPaybill());
        payload.put("resultCode",       event.getResultCode());
        payload.put("transactionDate",  event.getCompletedAt() != null
                                            ? event.getCompletedAt().toString() : null);
        return payload;
    }
}
