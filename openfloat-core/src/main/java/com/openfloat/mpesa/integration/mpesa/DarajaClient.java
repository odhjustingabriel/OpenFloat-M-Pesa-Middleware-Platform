package com.openfloat.mpesa.integration.mpesa;

import com.openfloat.mpesa.integration.mpesa.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class DarajaClient {

    private final DarajaConfig darajaConfig;
    private final DarajaTokenManager tokenManager;
    private final RestTemplate restTemplate = new RestTemplate();

    public StkPushResponse initiateStkPush(StkPushRequest request) {
        log.info("Sending STK Push request to Safaricom for phone: {}, amount: {}", request.getPhoneNumber(), request.getAmount());
        return postToDaraja(darajaConfig.getStkPushProcessUrl(), request, StkPushResponse.class);
    }

    public B2CResponse initiateB2cPayment(B2CRequest request) {
        log.info("Sending B2C Payment request to Safaricom for destination phone: {}, amount: {}", request.getPartyB(), request.getAmount());
        return postToDaraja(darajaConfig.getB2cPaymentUrl(), request, B2CResponse.class);
    }

    public ReversalResponse initiateReversal(ReversalRequest request) {
        log.info("Sending Reversal request to Safaricom for Transaction ID: {}", request.getTransactionId());
        return postToDaraja(darajaConfig.getReversalUrl(), request, ReversalResponse.class);
    }

    public C2BRegisterResponse registerC2bUrl(C2BRegisterRequest request) {
        log.info("Sending C2B URL Registration request to Safaricom for Shortcode: {}", request.getShortCode());
        return postToDaraja(darajaConfig.getC2bRegisterUrl(), request, C2BRegisterResponse.class);
    }

    private <T, R> R postToDaraja(String url, T body, Class<R> responseType) {
        String token = tokenManager.getAccessToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + token);
        headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

        HttpEntity<T> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<R> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    responseType
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            } else {
                log.error("API call to Daraja URL [{}] returned non-success code: {}", url, response.getStatusCode());
                throw new IllegalStateException("API call to Daraja failed with status: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Failed to perform request to Safaricom Daraja API URL [{}]: {}", url, e.getMessage(), e);
            throw new IllegalStateException("Safaricom Daraja API call failed: " + e.getMessage(), e);
        }
    }
}
