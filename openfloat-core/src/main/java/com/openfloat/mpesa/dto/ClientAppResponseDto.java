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
public class ClientAppResponseDto {

    private UUID id;
    private String clientName;
    private String accountPrefix;
    private String callbackUrl;
    private String status;
    private String registeredBy;
    private String notes;
    private Instant createdAt;
    private Instant updatedAt;
}
