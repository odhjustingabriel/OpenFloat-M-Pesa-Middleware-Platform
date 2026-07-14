package com.openfloat.mpesa.integration.mpesa.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Response payload from the Safaricom Daraja STK Push Query API.
 *
 * <p>Key result codes returned by Daraja:
 * <ul>
 *   <li>{@code 0}  — Transaction successful</li>
 *   <li>{@code 1}  — Transaction not found / still processing</li>
 *   <li>{@code 17} — M-Pesa system internal error</li>
 *   <li>{@code 1032} — Request cancelled by user</li>
 *   <li>{@code 1037} — Timeout waiting for user input</li>
 * </ul>
 */
@Data
public class StkQueryResponse {

    /** Indicates whether the overall Daraja API call succeeded. */
    @JsonProperty("ResponseCode")
    private String responseCode;

    @JsonProperty("ResponseDescription")
    private String responseDescription;

    /**
     * The merchant request ID that was returned when the STK Push was initiated.
     */
    @JsonProperty("MerchantRequestID")
    private String merchantRequestId;

    /**
     * The checkout request ID that was returned when the STK Push was initiated.
     */
    @JsonProperty("CheckoutRequestID")
    private String checkoutRequestId;

    /**
     * The final result code for the transaction.
     * {@code "0"} indicates success; any other value indicates failure.
     */
    @JsonProperty("ResultCode")
    private String resultCode;

    @JsonProperty("ResultDesc")
    private String resultDescription;

    /**
     * Returns {@code true} if Daraja reports the transaction as successfully completed.
     */
    public boolean isTransactionSuccessful() {
        return "0".equals(resultCode);
    }

    /**
     * Returns {@code true} if Daraja reports the transaction has failed definitively
     * (i.e., a known failure code that will not resolve with further waiting).
     */
    public boolean isTransactionFailed() {
        return resultCode != null
                && !"0".equals(resultCode)
                && !"1".equals(resultCode); // code 1 = still processing / not yet available
    }
}
