package com.openfloat.mpesa.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openfloat.mpesa.common.exception.ResourceNotFoundException;
import com.openfloat.mpesa.entity.AccountReferenceMapping;
import com.openfloat.mpesa.entity.ClientApp;
import com.openfloat.mpesa.entity.Transaction;
import com.openfloat.mpesa.entity.WebhookDeliveryLog;
import com.openfloat.mpesa.repository.AccountReferenceMappingRepository;
import com.openfloat.mpesa.repository.WebhookDeliveryLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

/**
 * Service responsible for dispatching real-time Webhook notifications to external
 * client applications when M-Pesa payments complete.
 * <p>
 * HMAC-SHA256 signatures are calculated over the JSON payload and attached via the
 * {@code X-OpenFloat-Signature} HTTP header for security.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings({"null", "unchecked"})
public class WebhookDispatcherService {

    private final AccountReferenceMappingRepository mappingRepository;
    private final WebhookDeliveryLogRepository logRepository;
    private final ObjectMapper objectMapper;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Asynchronously dispatches a payment webhook to the client system mapped to the transaction's account reference.
     */
    @Async
    @Transactional
    public void dispatchWebhookForTransaction(Transaction transaction) {
        if (transaction == null || transaction.getAccountReference() == null) {
            log.debug("No account reference present on transaction; skipping webhook dispatch");
            return;
        }

        Optional<AccountReferenceMapping> mappingOpt = mappingRepository.findByAccountReference(transaction.getAccountReference().trim());
        if (mappingOpt.isEmpty()) {
            log.debug("No registered client mapping found for account reference: {}", transaction.getAccountReference());
            return;
        }

        AccountReferenceMapping mapping = mappingOpt.get();
        ClientApp clientApp = mapping.getClientApp();

        if (!"ACTIVE".equalsIgnoreCase(clientApp.getStatus())) {
            log.warn("Client application '{}' (ID={}) is SUSPENDED; suppressing webhook dispatch",
                    clientApp.getClientName(), clientApp.getId());
            return;
        }

        // Mark mapping as PAID
        mapping.setStatus("PAID");
        mapping.setPaidAt(Instant.now());
        mapping.setTransaction(transaction);
        mappingRepository.save(mapping);

        // Construct Webhook JSON Payload
        Map<String, Object> payloadMap = new LinkedHashMap<>();
        payloadMap.put("event", "payment.success");
        payloadMap.put("accountReference", mapping.getAccountReference());
        payloadMap.put("transactionId", transaction.getId().toString());
        payloadMap.put("mpesaReceiptNumber", transaction.getTransactionId());
        payloadMap.put("amount", transaction.getAmount());
        payloadMap.put("phoneNumber", transaction.getPhoneNumber());
        payloadMap.put("paybill", transaction.getPaybill());
        payloadMap.put("status", transaction.getStatus().name());
        payloadMap.put("timestamp", Instant.now().toString());

        executeWebhookDispatch(transaction, clientApp, mapping.getCallbackUrl(), mapping.getAccountReference(), payloadMap, 1);
    }

    /**
     * Manually redrives a previously failed webhook delivery attempt.
     */
    @Transactional
    public WebhookDeliveryLog redriveWebhook(UUID logId) {
        WebhookDeliveryLog originalLog = logRepository.findById(logId)
                .orElseThrow(() -> new ResourceNotFoundException("Webhook delivery log not found with ID: " + logId));

        ClientApp clientApp = originalLog.getClientApp();
        String targetUrl = originalLog.getTargetUrl();
        Transaction transaction = originalLog.getTransaction();

        Map<String, Object> payloadMap;
        try {
            payloadMap = objectMapper.readValue(originalLog.getRequestPayload(), Map.class);
        } catch (Exception e) {
            payloadMap = new HashMap<>();
            payloadMap.put("event", "payment.redrive");
            payloadMap.put("accountReference", originalLog.getAccountReference());
        }

        int nextAttempt = originalLog.getAttemptNumber() + 1;

        return executeWebhookDispatch(transaction, clientApp, targetUrl, originalLog.getAccountReference(), payloadMap, nextAttempt);
    }

    private WebhookDeliveryLog executeWebhookDispatch(Transaction transaction, ClientApp clientApp, String targetUrl,
                                                       String accountReference, Map<String, Object> payloadMap, int attemptNumber) {
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payloadMap);
        } catch (Exception e) {
            log.error("Failed to serialize webhook payload map", e);
            payloadJson = "{}";
        }

        String signature = calculateHmacSha256(payloadJson, clientApp.getWebhookSecret());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-OpenFloat-Signature", signature);
        headers.set("X-OpenFloat-Event", String.valueOf(payloadMap.get("event")));
        headers.set("User-Agent", "OpenFloat-Middleware-Webhook/1.0");

        HttpEntity<String> entity = new HttpEntity<>(payloadJson, headers);

        Integer httpStatus = null;
        String responseBody = null;
        String errorMessage = null;
        boolean isSuccess = false;
        Instant deliveredAt = null;

        try {
            log.info("Dispatching Webhook attempt {} to ClientApp='{}' URL='{}'",
                    attemptNumber, clientApp.getClientName(), targetUrl);

            ResponseEntity<String> response = restTemplate.exchange(targetUrl, HttpMethod.POST, entity, String.class);
            httpStatus = response.getStatusCode().value();
            responseBody = response.getBody();
            if (responseBody != null && responseBody.length() > 2000) {
                responseBody = responseBody.substring(0, 2000);
            }

            if (response.getStatusCode().is2xxSuccessful()) {
                isSuccess = true;
                deliveredAt = Instant.now();
                log.info("Webhook dispatch SUCCESS to ClientApp='{}' HTTP {}", clientApp.getClientName(), httpStatus);
            } else {
                errorMessage = "Client returned non-2xx status code: " + httpStatus;
                log.warn("Webhook dispatch FAILED to ClientApp='{}' HTTP {}", clientApp.getClientName(), httpStatus);
            }
        } catch (HttpStatusCodeException e) {
            httpStatus = e.getStatusCode().value();
            responseBody = e.getResponseBodyAsString();
            if (responseBody != null && responseBody.length() > 2000) {
                responseBody = responseBody.substring(0, 2000);
            }
            errorMessage = "HTTP Error " + httpStatus + ": " + e.getStatusText();
            log.warn("Webhook dispatch error for ClientApp='{}': {}", clientApp.getClientName(), errorMessage);
        } catch (Exception e) {
            errorMessage = "Network or I/O failure: " + e.getMessage();
            log.error("Network failure delivering webhook to ClientApp='{}': {}", clientApp.getClientName(), e.getMessage());
        }

        WebhookDeliveryLog deliveryLog = WebhookDeliveryLog.builder()
                .transaction(transaction)
                .clientApp(clientApp)
                .accountReference(accountReference)
                .targetUrl(targetUrl)
                .httpStatus(httpStatus)
                .requestPayload(payloadJson)
                .responseBody(responseBody)
                .errorMessage(errorMessage)
                .attemptNumber(attemptNumber)
                .success(isSuccess)
                .deliveredAt(deliveredAt)
                .build();

        return logRepository.save(deliveryLog);
    }

    private String calculateHmacSha256(String data, String secret) {
        try {
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(secretKey);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hmacBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Error computing HMAC-SHA256 signature", e);
            return "";
        }
    }
}
