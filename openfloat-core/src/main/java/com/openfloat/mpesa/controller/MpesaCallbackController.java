package com.openfloat.mpesa.controller;

import com.openfloat.mpesa.service.CallbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/mpesa/callbacks")
@RequiredArgsConstructor
@Tag(name = "M-Pesa Callbacks", description = "Safaricom M-Pesa Daraja API callback endpoints")
public class MpesaCallbackController {

    private final CallbackService callbackService;

    @PostMapping("/stk")
    @Operation(summary = "Lipa na M-Pesa Online STK Push callback")
    public ResponseEntity<Map<String, Object>> handleStkCallback(@RequestBody Map<String, Object> payload) {
        log.info("STK Callback received: {}", payload);
        callbackService.processStkCallback(payload);
        return ResponseEntity.ok(Map.of("ResultCode", 0, "ResultDesc", "Success"));
    }

    @PostMapping("/c2b")
    @Operation(summary = "C2B validation and confirmation callbacks")
    public ResponseEntity<Map<String, Object>> handleC2bCallback(
            @RequestParam(required = false, defaultValue = "confirmation") String type,
            @RequestBody Map<String, Object> payload) {
        log.info("C2B Callback received of type [{}]: {}", type, payload);
        Map<String, Object> response = callbackService.processC2bCallback(type, payload);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/b2c")
    @Operation(summary = "B2C payment result/timeout callback")
    public ResponseEntity<Map<String, Object>> handleB2cCallback(@RequestBody Map<String, Object> payload) {
        log.info("B2C Callback received: {}", payload);
        callbackService.processB2cCallback(payload);
        return ResponseEntity.ok(Map.of("ResultCode", 0, "ResultDesc", "Success"));
    }

    @PostMapping("/reversal")
    @Operation(summary = "Transaction Reversal result/timeout callback")
    public ResponseEntity<Map<String, Object>> handleReversalCallback(@RequestBody Map<String, Object> payload) {
        log.info("Reversal Callback received: {}", payload);
        callbackService.processReversalCallback(payload);
        return ResponseEntity.ok(Map.of("ResultCode", 0, "ResultDesc", "Success"));
    }
}
