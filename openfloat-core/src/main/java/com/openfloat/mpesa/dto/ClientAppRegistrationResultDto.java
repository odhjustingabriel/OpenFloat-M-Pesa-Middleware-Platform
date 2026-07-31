package com.openfloat.mpesa.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientAppRegistrationResultDto {

    private UUID id;
    private String clientName;
    private String accountPrefix;
    private String callbackUrl;
    private String status;
    private String registeredBy;
    private String notes;
    private Instant createdAt;

    /**
     * Plaintext API key issued ONCE upon registration.
     * Stored as SHA-256 hash in DB; never exposed again after creation.
     */
    private String apiKey;

    /**
     * Plaintext HMAC-SHA256 secret issued ONCE for signature verification.
     * Used by client apps to verify X-OpenFloat-Signature headers.
     */
    private String webhookSecret;
}
