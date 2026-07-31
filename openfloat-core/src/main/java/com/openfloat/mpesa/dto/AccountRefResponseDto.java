package com.openfloat.mpesa.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountRefResponseDto {

    private UUID id;
    private String accountReference;
    private UUID clientAppId;
    private String clientAppName;
    private String accountPrefix;
    private String callbackUrl;
    private BigDecimal requestedAmount;
    private String currency;
    private String description;
    private String status;
    private Instant expiresAt;
    private Instant paidAt;
    private String transactionId;
    private Instant createdAt;
}
