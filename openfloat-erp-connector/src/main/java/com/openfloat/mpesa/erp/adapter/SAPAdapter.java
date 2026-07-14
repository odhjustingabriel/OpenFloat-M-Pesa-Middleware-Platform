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
 * ERP adapter for SAP S/4HANA or SAP Business One via REST.
 *
 * <p>Authentication: HTTP Basic Auth using {@code client-id} as username and
 * {@code client-secret} as password, Base64-encoded in the {@code Authorization} header.
 * This matches the default SAP OData / Business One Service Layer authentication scheme.
 *
 * <p>Payload: A minimal BAPI-compatible financial document entry. Adjust field names
 * to match the specific SAP REST endpoint exposed in your landscape.
 */
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
        log.info("SAP Adapter: Dispatching txnId={} to SAP endpoint: {}", event.getTransactionId(), baseUrl);

        HttpHeaders headers = buildHeaders();
        Map<String, Object> payload = buildPayload(event);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

        try {
            String response = restTemplate.postForObject(
                    baseUrl + "/transactions",
                    request,
                    String.class);
            log.info("SAP Adapter: Sync successful for txnId={}. SAP response: {}", event.getTransactionId(), response);
        } catch (Exception e) {
            log.error("SAP Adapter: Sync FAILED for txnId={}. Error: {}", event.getTransactionId(), e.getMessage());
            throw e;
        }
    }

    @Override
    public String getSystemName() {
        return "sap";
    }

    // ── Private helpers ───────────────────────────────────────────────────

    /**
     * Builds HTTP headers with Basic Auth encoding.
     * Basic auth = Base64(clientId:clientSecret) per RFC 7617.
     */
    private HttpHeaders buildHeaders() {
        String credentials = clientId + ":" + clientSecret;
        String encoded = Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.AUTHORIZATION, "Basic " + encoded);
        return headers;
    }

    /**
     * Builds the SAP financial document payload from the transaction event.
     * Field names mirror a standard SAP REST/BAPI financial posting request.
     */
    private Map<String, Object> buildPayload(TransactionCompletedEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("documentDate",      event.getCompletedAt() != null ? event.getCompletedAt().toString() : null);
        payload.put("amount",            event.getAmount());
        payload.put("currency",          "KES");
        payload.put("accountReference",  event.getAccountReference());
        payload.put("mpesaReceipt",      event.getReconciliationId() != null
                                             ? event.getReconciliationId()
                                             : event.getTransactionId());
        payload.put("msisdn",            event.getPhoneNumber());
        payload.put("transactionType",   event.getTransactionType());
        payload.put("shortcode",         event.getPaybill());
        payload.put("resultCode",        event.getResultCode());
        payload.put("resultDescription", event.getResultDescription());
        return payload;
    }
}
