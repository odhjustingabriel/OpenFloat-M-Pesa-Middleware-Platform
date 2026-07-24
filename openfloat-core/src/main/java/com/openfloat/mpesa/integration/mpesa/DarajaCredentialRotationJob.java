package com.openfloat.mpesa.integration.mpesa;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Automated Production Job for Safaricom Daraja API Credential & Token Rotation.
 * Periodically verifies OAuth access token health, performs proactive token renewal,
 * and increments observability counters for security auditing.
 */
@Slf4j
@Component
public class DarajaCredentialRotationJob {

    private final DarajaTokenManager tokenManager;
    private final DarajaConfig darajaConfig;
    private final Counter rotationSuccessCounter;
    private final Counter rotationFailureCounter;

    public DarajaCredentialRotationJob(DarajaTokenManager tokenManager,
                                       DarajaConfig darajaConfig,
                                       MeterRegistry meterRegistry) {
        this.tokenManager = tokenManager;
        this.darajaConfig = darajaConfig;
        this.rotationSuccessCounter = meterRegistry.counter("daraja.credential.rotation.count", "status", "success");
        this.rotationFailureCounter = meterRegistry.counter("daraja.credential.rotation.count", "status", "failure");
    }

    /**
     * Executes scheduled credential health check & token rotation.
     * Default schedule: Every 6 hours (00:00, 06:00, 12:00, 18:00 UTC).
     */
    @Scheduled(cron = "${openfloat.mpesa.rotation.cron:0 0 */6 * * *}")
    public void rotateAndVerifyCredentials() {
        log.info("Starting scheduled Daraja credential verification and token rotation job at {}", Instant.now());
        try {
            // 1. Verify consumer key & secret presence
            if (darajaConfig.getConsumerKey() == null || darajaConfig.getConsumerKey().isBlank()) {
                throw new IllegalStateException("Daraja Consumer Key is missing or unconfigured");
            }
            if (darajaConfig.getConsumerSecret() == null || darajaConfig.getConsumerSecret().isBlank()) {
                throw new IllegalStateException("Daraja Consumer Secret is missing or unconfigured");
            }

            // 2. Force token cache refresh to ensure Daraja connectivity & credential validity
            tokenManager.evictToken();
            String freshToken = tokenManager.getAccessToken();

            if (freshToken == null || freshToken.isBlank()) {
                throw new IllegalStateException("Received empty access token from Daraja OAuth endpoint");
            }

            rotationSuccessCounter.increment();
            log.info("Successfully verified and rotated Daraja OAuth access token. Environment: {}",
                    darajaConfig.getBaseUrl());

        } catch (Exception e) {
            rotationFailureCounter.increment();
            log.error("CRITICAL: Daraja credential rotation and verification failed! Reason: {}", e.getMessage(), e);
        }
    }
}
