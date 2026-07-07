package com.openfloat.mpesa.integration.mpesa.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class C2BRegisterRequest {

    @JsonProperty("ShortCode")
    private String shortCode;

    @JsonProperty("ResponseType")
    private String responseType;

    @JsonProperty("ConfirmationURL")
    private String confirmationUrl;

    @JsonProperty("ValidationURL")
    private String validationUrl;
}
