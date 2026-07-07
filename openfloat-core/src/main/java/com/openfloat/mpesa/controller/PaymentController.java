package com.openfloat.mpesa.controller;

import com.openfloat.mpesa.common.dto.ApiResponse;
import com.openfloat.mpesa.dto.StkPushRequestDto;
import com.openfloat.mpesa.dto.StkPushResponseDto;
import com.openfloat.mpesa.service.B2CService;
import com.openfloat.mpesa.service.C2BService;
import com.openfloat.mpesa.service.ReversalService;
import com.openfloat.mpesa.service.StkPushService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payment Operations", description = "Initiate STK Push, B2C disbursements, reversals, and C2B operations")
public class PaymentController {

    private final StkPushService stkPushService;
    private final B2CService b2cService;
    private final ReversalService reversalService;
    private final C2BService c2bService;

    @PostMapping("/stk-push")
    @Operation(summary = "Initiate Lipa na M-Pesa Online STK Push Prompt")
    public ResponseEntity<ApiResponse<StkPushResponseDto>> initiateStkPush(
            @Valid @RequestBody StkPushRequestDto requestDto) {
        log.info("Received STK Push API request for {}", requestDto.getMsisdn());
        StkPushResponseDto response = stkPushService.initiateStkPush(requestDto);
        return ResponseEntity.ok(ApiResponse.success(response, "STK Push prompt initiated successfully"));
    }

    @PostMapping("/b2c")
    @Operation(summary = "Disburse funds from Business Shortcode to Customer Mobile Wallet")
    public ResponseEntity<ApiResponse<Map<String, Object>>> initiateB2c(
            @RequestParam String phoneNumber,
            @RequestParam BigDecimal amount,
            @RequestParam(required = false, defaultValue = "PromotionPayment") String commandId,
            @RequestParam(required = false, defaultValue = "Disbursement") String remarks,
            @RequestParam(required = false) String occasion) {
        log.info("Received B2C payout API request to {}", phoneNumber);
        UUID transactionId = b2cService.initiateDisbursement(phoneNumber, amount, commandId, remarks, occasion);
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("transactionId", transactionId, "status", "PENDING"),
                "B2C disbursement transaction initiated successfully"
        ));
    }

    @PostMapping("/reversal")
    @Operation(summary = "Initiate reversal of an erroneous transaction")
    public ResponseEntity<ApiResponse<Map<String, Object>>> initiateReversal(
            @RequestParam String transactionId,
            @RequestParam(required = false, defaultValue = "Erroneous Payment Reversal") String remarks) {
        log.info("Received Reversal API request for Transaction ID: {}", transactionId);
        UUID reversalTransactionId = reversalService.initiateReversal(transactionId, remarks);
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("transactionId", reversalTransactionId, "status", "PENDING"),
                "Reversal request initiated successfully"
        ));
    }

    @PostMapping("/c2b/register-urls")
    @Operation(summary = "Register C2B Confirmation and Validation URLs with Safaricom")
    public ResponseEntity<ApiResponse<Map<String, Object>>> registerC2bUrls() {
        log.info("Received request to register C2B validation and confirmation URLs");
        c2bService.registerUrls();
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("registered", true),
                "C2B URLs registered successfully"
        ));
    }

    @PostMapping("/c2b/simulate")
    @Operation(summary = "Simulate C2B payment transaction (Sandbox only)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> simulateC2bPayment(
            @RequestParam String phoneNumber,
            @RequestParam BigDecimal amount,
            @RequestParam String billRefNumber) {
        log.info("Received C2B Simulation request for phone: {}, amount: {}", phoneNumber, amount);
        Map<String, Object> response = c2bService.simulateTransaction(phoneNumber, amount, billRefNumber);
        return ResponseEntity.ok(ApiResponse.success(
                response,
                "C2B Simulation request submitted"
        ));
    }
}
