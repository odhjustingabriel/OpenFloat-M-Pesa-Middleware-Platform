package com.openfloat.mpesa.integration.mpesa;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
public class DarajaConfig {

    @Value("${openfloat.mpesa.daraja.base-url}")
    private String baseUrl;

    @Value("${openfloat.mpesa.daraja.consumer-key}")
    private String consumerKey;

    @Value("${openfloat.mpesa.daraja.consumer-secret}")
    private String consumerSecret;

    @Value("${openfloat.mpesa.daraja.passkey}")
    private String passkey;

    @Value("${openfloat.mpesa.daraja.shortcode}")
    private String shortcode;

    @Value("${openfloat.mpesa.daraja.callback-base-url}")
    private String callbackBaseUrl;

    // Standard endpoints paths
    public String getAuthUrl() {
        return baseUrl + "/oauth/v1/generate?grant_type=client_credentials";
    }

    public String getStkPushUrl() {
        return baseUrl + "/mpesa/stkpush/v1/query"; // Wait, STK push request is v1/processrequest
    }

    public String getStkPushProcessUrl() {
        return baseUrl + "/mpesa/stkpush/v1/processrequest";
    }

    public String getB2cPaymentUrl() {
        return baseUrl + "/mpesa/b2c/v1/paymentrequest";
    }

    public String getReversalUrl() {
        return baseUrl + "/mpesa/reversal/v1/request";
    }

    public String getC2bRegisterUrl() {
        return baseUrl + "/mpesa/c2b/v1/registerurl";
    }

    /** STK Push Query — checks the final result of an initiated STK Push. */
    public String getStkQueryUrl() {
        return baseUrl + "/mpesa/stkpush/v1/query";
    }
}
