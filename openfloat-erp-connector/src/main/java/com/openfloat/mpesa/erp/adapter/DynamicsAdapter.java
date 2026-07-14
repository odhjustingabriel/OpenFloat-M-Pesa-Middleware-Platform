package com.openfloat.mpesa.erp.adapter;

import com.openfloat.mpesa.common.event.TransactionCompletedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ERP adapter for Microsoft Dynamics 365 Business Central via REST API.
 *
 * <p>Authentication: OAuth2 Client Credentials flow against the Microsoft
 * identity platform ({@code https://login.microsoftonline.com/{tenantId}/oauth2/v2.0/token}).
 * A cached bearer token is reused until it expires (minus a 60-second safety margin).
 * On expiry or on a 401 response the token is automatically refreshed.
 *
 * <p>Payload: Mapped to a Dynamics 365 Business Central General Ledger Journal
 * entry via the standard Business Central REST API.
 */
@Slf4j
@Component
public class DynamicsAdapter implements ERPAdapter {

    private static final String MICROSOFT_TOKEN_URL =
            "https://login.microsoftonline.com/%s/oauth2/v2.0/token";
    private static final String DYNAMICS_SCOPE =
            "https://api.businesscentral.dynamics.com/.default";

    @Value("${openfloat.erp.dynamics.base-url}")
    private String baseUrl;

    @Value("${openfloat.erp.dynamics.tenant-id}")
    private String tenantId;

    @Value("${openfloat.erp.dynamics.client-id}")
    private String clientId;

    @Value("${openfloat.erp.dynamics.client-secret}")
    private String clientSecret;

    private final RestTemplate restTemplate = new RestTemplate();

    /** Cached access token and its expiry time. */
    private final AtomicReference<String> cachedToken     = new AtomicReference<>();
    private volatile Instant              tokenExpiresAt  = Instant.EPOCH;

    @Override
    public void sendTransaction(TransactionCompletedEvent event) throws Exception {
        log.info("Dynamics 365 Adapter: Dispatching txnId={} for tenant: {}",
                event.getTransactionId(), tenantId);

        String token = getAccessToken();
        HttpHeaders headers = buildHeaders(token);
        Map<String, Object> payload = buildPayload(event);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

        try {
            String response = restTemplate.postForObject(
                    baseUrl + "/general-journals",
                    request,
                    String.class);
            log.info("Dynamics 365 Adapter: Sync successful for txnId={}. BC response: {}",
                    event.getTransactionId(), response);
        } catch (org.springframework.web.client.HttpClientErrorException.Unauthorized e) {
            // Token may have been revoked; evict and retry once
            log.warn("Dynamics 365 Adapter: 401 on txnId={}. Refreshing token and retrying.",
                    event.getTransactionId());
            tokenExpiresAt = Instant.EPOCH; // force refresh
            token = getAccessToken();
            headers.setBearerAuth(token);
            request = new HttpEntity<>(payload, headers);
            String response = restTemplate.postForObject(baseUrl + "/general-journals", request, String.class);
            log.info("Dynamics 365 Adapter: Retry sync successful for txnId={}", event.getTransactionId());
        } catch (Exception e) {
            log.error("Dynamics 365 Adapter: Sync FAILED for txnId={}. Error: {}",
                    event.getTransactionId(), e.getMessage());
            throw e;
        }
    }

    @Override
    public String getSystemName() {
        return "dynamics";
    }

    // ── Private helpers ───────────────────────────────────────────────────

    /**
     * Returns a valid Bearer token for the Microsoft identity platform.
     * Reuses the cached token until it is within 60 seconds of expiry.
     */
    @SuppressWarnings("unchecked")
    private synchronized String getAccessToken() {
        if (cachedToken.get() != null && Instant.now().isBefore(tokenExpiresAt)) {
            return cachedToken.get();
        }

        log.info("Dynamics 365 Adapter: Acquiring new OAuth2 access token for tenant={}", tenantId);

        String tokenUrl = String.format(MICROSOFT_TOKEN_URL, tenantId);

        MultiValueMap<String, String> formParams = new LinkedMultiValueMap<>();
        formParams.add("grant_type",    "client_credentials");
        formParams.add("client_id",     clientId);
        formParams.add("client_secret", clientSecret);
        formParams.add("scope",         DYNAMICS_SCOPE);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<MultiValueMap<String, String>> tokenRequest = new HttpEntity<>(formParams, headers);

        Map<String, Object> tokenResponse = restTemplate.postForObject(tokenUrl, tokenRequest, Map.class);

        if (tokenResponse == null || !tokenResponse.containsKey("access_token")) {
            throw new IllegalStateException("Dynamics 365: Failed to obtain access token — empty response.");
        }

        String accessToken = (String) tokenResponse.get("access_token");
        int expiresIn = tokenResponse.containsKey("expires_in")
                ? Integer.parseInt(tokenResponse.get("expires_in").toString())
                : 3600;

        // Cache token, expiring 60 seconds early for safety margin
        tokenExpiresAt = Instant.now().plusSeconds(expiresIn - 60L);
        cachedToken.set(accessToken);

        log.info("Dynamics 365 Adapter: Token acquired. Expires in {}s (cached until {})",
                expiresIn, tokenExpiresAt);

        return accessToken;
    }

    private HttpHeaders buildHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }

    /**
     * Maps a completed M-Pesa transaction to a Dynamics 365 Business Central
     * General Journal entry payload.
     */
    private Map<String, Object> buildPayload(TransactionCompletedEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("journalBatchName", "MPESA_PYMT");
        payload.put("lineNo",           System.currentTimeMillis());
        payload.put("documentNo",       event.getReconciliationId() != null
                                            ? event.getReconciliationId()
                                            : event.getTransactionId());
        payload.put("documentDate",     event.getCompletedAt() != null
                                            ? event.getCompletedAt().toString() : null);
        payload.put("accountType",      "Customer");
        payload.put("accountNo",        event.getAccountReference());
        payload.put("amount",           event.getAmount());
        payload.put("currencyCode",     "KES");
        payload.put("description",      String.format("M-Pesa %s - %s",
                                            event.getTransactionType(), event.getPhoneNumber()));
        payload.put("externalDocumentNo", event.getTransactionId());
        return payload;
    }
}
