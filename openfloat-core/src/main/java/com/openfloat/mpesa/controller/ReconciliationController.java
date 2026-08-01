package com.openfloat.mpesa.controller;

import com.openfloat.mpesa.common.dto.ApiResponse;
import com.openfloat.mpesa.dto.ReconciliationOverrideRequestDto;
import com.openfloat.mpesa.service.ReconciliationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller exposing endpoints for Manager payment reconciliation overrides and discrepancy resolution.
 */
@RestController
@RequestMapping("/api/v1/reconciliation")
@RequiredArgsConstructor
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    /**
     * Execute a manual reconciliation override for an unmatched payment.
     */
    @PostMapping("/override")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> manualOverride(
            @Valid @RequestBody ReconciliationOverrideRequestDto dto,
            Authentication authentication) {

        String managerUsername = authentication != null ? authentication.getName() : "MANAGER";
        Map<String, Object> result = reconciliationService.executeManualOverride(dto, managerUsername);
        return ResponseEntity.ok(ApiResponse.success(result, "Manual reconciliation override executed successfully"));
    }
}
