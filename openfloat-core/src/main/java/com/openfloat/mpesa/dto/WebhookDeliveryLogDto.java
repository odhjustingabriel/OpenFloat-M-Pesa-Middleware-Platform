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
public class WebhookDeliveryLogDto {

    private UUID id;
    private UUID transactionId;
    private UUID clientAppId;
    private String clientName;
    private String accountReference;
    private String targetUrl;
    private Integer httpStatus;
    private String requestPayload;
    private String responseBody;
    private String errorMessage;
    private Integer attemptNumber;
    private boolean success;
    private Instant deliveredAt;
    private Instant createdAt;
}
