package com.openfloat.mpesa.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StkPushRequestDto {

    @NotBlank(message = "MSISDN is required")
    @Pattern(regexp = "^(254|0|\\+254)[17]\\d{8}$", message = "Invalid Kenyan phone number format")
    private String msisdn;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "1.00", message = "Amount must be at least 1.00")
    private BigDecimal amount;

    @NotBlank(message = "Paybill is required")
    private String paybill;

    @NotBlank(message = "Account reference is required")
    private String accountRef;

    @NotBlank(message = "Description is required")
    private String description;
}
