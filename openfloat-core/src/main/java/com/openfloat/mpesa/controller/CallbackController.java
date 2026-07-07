package com.openfloat.mpesa.controller;

import com.openfloat.mpesa.common.dto.ApiResponse;
import com.openfloat.mpesa.dto.CallbackPayloadDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/callbacks")
@Tag(name = "Internal Integration Callbacks", description = "Mock receiver endpoints simulating internal enterprise systems")
public class CallbackController {

    @PostMapping
    @Operation(summary = "Forward transaction results to internal applications")
    public ResponseEntity<ApiResponse<Map<String, Object>>> receiveInternalCallback(
            @RequestBody CallbackPayloadDto payload) {
        log.info("Received internal integration callback for Transaction [{}]: Status={}, Amount={}", 
                payload.getTransactionId(), payload.getStatus(), payload.getAmount());
        
        // Simulating processing logic in a downstream application (e.g., Core Banking, billing, etc.)
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("acknowledged", true, "receivedAt", java.time.Instant.now().toString()),
                "Downstream system acknowledged transaction update"
        ));
    }
}
