package com.openfloat.mpesa.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountRefGenerateRequestDto {

    @NotBlank(message = "Account prefix or client application ID is required")
    private String accountPrefix;

    @DecimalMin(value = "1.00", message = "Requested amount must be at least KES 1.00")
    private BigDecimal requestedAmount;

    private String description;

    /**
     * Optional custom callback URL override for this specific transaction.
     * If omitted, the default callback URL registered on the ClientApp is used.
     */
    private String callbackUrlOverride;

    /**
     * Time-to-live in minutes (default: 1440 mins = 24 hours).
     */
    @Builder.Default
    private Integer ttlMinutes = 1440;
}
