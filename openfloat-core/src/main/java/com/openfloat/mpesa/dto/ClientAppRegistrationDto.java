package com.openfloat.mpesa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientAppRegistrationDto {

    @NotBlank(message = "Client name is required")
    private String clientName;

    @NotBlank(message = "Account prefix is required")
    @Pattern(regexp = "^[A-Z0-9]{2,10}$", message = "Account prefix must be 2-10 uppercase alphanumeric characters (e.g. ECOMM, SCH)")
    private String accountPrefix;

    @NotBlank(message = "Callback URL is required")
    @Pattern(regexp = "^https?://.*$", message = "Callback URL must be a valid HTTP or HTTPS URL")
    private String callbackUrl;

    private String notes;
}
