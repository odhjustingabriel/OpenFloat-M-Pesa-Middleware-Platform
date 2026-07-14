package com.openfloat.mpesa.controller;

import com.openfloat.mpesa.audit.AuditService;
import com.openfloat.mpesa.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
@Tag(name = "Audit Chain Verification", description = "Endpoints for verifying the integrity of the audit logs chain")
@PreAuthorize("hasRole('ADMIN')")
public class AuditIntegrityController {

    private final AuditService auditService;

    @GetMapping("/verify")
    @Operation(summary = "Verify integrity of the tamper-evident audit log chain")
    public ResponseEntity<ApiResponse<Map<String, Object>>> verifyAuditChain() {
        log.info("Request received to verify audit log chain integrity");
        boolean isUnbroken = auditService.verifyChainIntegrity();
        
        Map<String, Object> result = new HashMap<>();
        result.put("verified", isUnbroken);
        
        if (isUnbroken) {
            result.put("message", "Audit log chain is verified and completely unbroken.");
            return ResponseEntity.ok(ApiResponse.success(result, "Verification successful"));
        } else {
            result.put("message", "Audit log chain has been corrupted or tampered with.");
            return ResponseEntity.ok(ApiResponse.success(result, "Verification detected tampering"));
        }
    }
}
