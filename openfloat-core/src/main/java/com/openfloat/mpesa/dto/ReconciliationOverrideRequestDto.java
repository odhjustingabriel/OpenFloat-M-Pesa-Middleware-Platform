package com.openfloat.mpesa.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationOverrideRequestDto {

    private UUID transactionId;

    @NotBlank(message = "Account reference is required")
    private String accountReference;

    @NotBlank(message = "Reason for manual override is required")
    private String reason;
}
