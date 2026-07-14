package com.openfloat.mpesa.integration.mpesa.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

/**
 * Request payload for the Safaricom Daraja STK Push Query API.
 * Used by the reconciliation scheduler to check the final result
 * of transactions that remain in PENDING status.
 *
 * <p>Endpoint: {@code POST /mpesa/stkpush/v1/query}
 */
@Data
@Builder
public class StkQueryRequest {

    /**
     * Business short code (paybill number) used to initiate the STK Push.
     */
    @JsonProperty("BusinessShortCode")
    private String businessShortCode;

    /**
     * Lipa Na M-Pesa online password. Base64(ShortCode + Passkey + Timestamp).
     */
    @JsonProperty("Password")
    private String password;

    /**
     * Timestamp in yyyyMMddHHmmss format used to generate the password.
     */
    @JsonProperty("Timestamp")
    private String timestamp;

    /**
     * The CheckoutRequestID received when the STK Push was initiated.
     */
    @JsonProperty("CheckoutRequestID")
    private String checkoutRequestId;
}
