package com.openfloat.mpesa.integration.mpesa;

import com.openfloat.mpesa.integration.mpesa.dto.DarajaTokenResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
@SuppressWarnings("null")
public class DarajaTokenManager {

    private static final String REDIS_TOKEN_KEY = "daraja:access_token";
    private final DarajaConfig darajaConfig;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Retrieves a valid M-Pesa Daraja API access token.
     * Checks Redis cache first. If expired or not present, fetches from Daraja and caches it.
     */
    public String getAccessToken() {
        try {
            String cachedToken = (String) redisTemplate.opsForValue().get(REDIS_TOKEN_KEY);
            if (cachedToken != null) {
                log.debug("Using cached Daraja access token");
                return cachedToken;
            }
        } catch (Exception e) {
            log.warn("Failed to retrieve access token from Redis cache, falling back to direct API call: {}", e.getMessage());
        }

        return fetchNewAccessToken();
    }

    private synchronized String fetchNewAccessToken() {
        // Double check cache in case another thread populated it while waiting
        try {
            String cachedToken = (String) redisTemplate.opsForValue().get(REDIS_TOKEN_KEY);
            if (cachedToken != null) {
                return cachedToken;
            }
        } catch (Exception e) {
            // Ignore cache error
        }

        log.info("Fetching new access token from Safaricom Daraja API...");
        
        String authHeaderValue = getBasicAuthHeader();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", authHeaderValue);
        headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        try {
            ResponseEntity<DarajaTokenResponse> response = restTemplate.exchange(
                    darajaConfig.getAuthUrl(),
                    HttpMethod.GET,
                    requestEntity,
                    DarajaTokenResponse.class
            );

            DarajaTokenResponse body = response.getBody();
            if (response.getStatusCode().is2xxSuccessful() && body != null) {
                String token = body.getAccessToken();
                // Expiry from Daraja is usually in seconds (e.g. "3599")
                long expiresInSeconds = Long.parseLong(body.getExpiresIn());
                
                // Buffer by 5 minutes to prevent edge-case token expiration failures
                long cacheExpiry = Math.max(60, expiresInSeconds - 300);

                try {
                    redisTemplate.opsForValue().set(REDIS_TOKEN_KEY, token, cacheExpiry, TimeUnit.SECONDS);
                    log.info("Successfully cached new Daraja token for {} seconds", cacheExpiry);
                } catch (Exception e) {
                    log.warn("Failed to save Daraja access token to Redis: {}", e.getMessage());
                }

                return token;
            } else {
                throw new IllegalStateException("Unexpected token response status: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Failed to fetch access token from Daraja API: {}", e.getMessage(), e);
            throw new IllegalStateException("Authentication with Safaricom Daraja API failed", e);
        }
    }

    private String getBasicAuthHeader() {
        String rawHeader = darajaConfig.getConsumerKey() + ":" + darajaConfig.getConsumerSecret();
        return "Basic " + Base64.getEncoder().encodeToString(rawHeader.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Evicts the currently cached access token from Redis.
     */
    public void evictToken() {
        try {
            redisTemplate.delete(REDIS_TOKEN_KEY);
            log.info("Evicted Daraja access token from Redis");
        } catch (Exception e) {
            log.warn("Failed to evict Daraja access token from Redis: {}", e.getMessage());
        }
    }
}
