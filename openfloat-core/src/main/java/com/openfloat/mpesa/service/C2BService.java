package com.openfloat.mpesa.service;

import com.openfloat.mpesa.common.util.PhoneNumberUtils;
import com.openfloat.mpesa.integration.mpesa.DarajaClient;
import com.openfloat.mpesa.integration.mpesa.DarajaConfig;
import com.openfloat.mpesa.integration.mpesa.DarajaTokenManager;
import com.openfloat.mpesa.integration.mpesa.dto.C2BRegisterRequest;
import com.openfloat.mpesa.integration.mpesa.dto.C2BRegisterResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class C2BService {

    private final DarajaClient darajaClient;
    private final DarajaConfig darajaConfig;
    private final DarajaTokenManager tokenManager;
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Registers validation and confirmation URL endpoints with Safaricom Daraja API.
     */
    public C2BRegisterResponse registerUrls() {
        String callbackBase = darajaConfig.getCallbackBaseUrl();
        C2BRegisterRequest request = C2BRegisterRequest.builder()
                .shortCode(darajaConfig.getShortcode())
                .responseType("Completed") // Or "Cancelled"
                .confirmationUrl(callbackBase + "/api/v1/mpesa/callbacks/c2b?type=confirmation")
                .validationUrl(callbackBase + "/api/v1/mpesa/callbacks/c2b?type=validation")
                .build();

        log.info("Registering C2B URLs with Safaricom: Confirmation=[{}], Validation=[{}]", 
                request.getConfirmationUrl(), request.getValidationUrl());
        return darajaClient.registerC2bUrl(request);
    }

    /**
     * Simulates a C2B payment transaction (Only valid in Safaricom Sandbox environment).
     */
    @Transactional
    @SuppressWarnings({ "null", "unchecked", "rawtypes" })
    public Map<String, Object> simulateTransaction(String msisdn, BigDecimal amount, String billRefNumber) {
        String normalizedPhone = PhoneNumberUtils.normalize(msisdn);
        String url = darajaConfig.getBaseUrl() + "/mpesa/c2b/v1/simulate";

        String token = tokenManager.getAccessToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        Map<String, Object> body = new HashMap<>();
        body.put("ShortCode", darajaConfig.getShortcode());
        body.put("CommandID", "CustomerPayBillOnline");
        body.put("Amount", amount.intValue());
        body.put("Msisdn", normalizedPhone);
        body.put("BillRefNumber", billRefNumber);

        log.info("Simulating C2B transaction for phone: {}, amount: {}, ref: {}", normalizedPhone, amount, billRefNumber);
        
        try {
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            Map<String, Object> responseBody = response.getBody();
            return responseBody;
        } catch (Exception e) {
            log.error("Failed to simulate C2B transaction: {}", e.getMessage(), e);
            throw new IllegalStateException("C2B Simulation failed: " + e.getMessage(), e);
        }
    }
}
