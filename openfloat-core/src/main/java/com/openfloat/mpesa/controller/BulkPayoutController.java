package com.openfloat.mpesa.controller;

import com.openfloat.mpesa.audit.Audit;
import com.openfloat.mpesa.audit.AuditEventType;
import com.openfloat.mpesa.common.dto.ApiResponse;
import com.openfloat.mpesa.dto.BulkPayoutItemDto;
import com.openfloat.mpesa.dto.BulkPayoutResultDto;
import com.openfloat.mpesa.service.BulkPayoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;

/**
 * Controller exposing bulk B2C payout disbursement endpoints (JSON list and CSV file upload).
 *
 * <p>Phase 9 — Component 4: Bulk Payouts & Batch B2C Disbursement Engine</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/payments/b2c/bulk")
@RequiredArgsConstructor
@Tag(name = "Bulk B2C Payouts", description = "Initiate batch mobile money disbursements via JSON list or CSV file upload")
public class BulkPayoutController {

    private final BulkPayoutService bulkPayoutService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Initiate a batch of B2C payouts via JSON list")
    @Audit(action = AuditEventType.PAYMENT_INITIATED, resource = "BULK_B2C_JSON")
    public ResponseEntity<ApiResponse<BulkPayoutResultDto>> processBulkPayouts(
            @Valid @RequestBody List<BulkPayoutItemDto> items) {
        log.info("Received request to process JSON bulk B2C payout batch with {} items", items != null ? items.size() : 0);
        BulkPayoutResultDto result = bulkPayoutService.processBulkPayouts(items);
        return ResponseEntity.ok(ApiResponse.success(result, "Bulk B2C payout batch processed successfully"));
    }

    @PostMapping(value = "/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Upload a CSV file of beneficiary payout records for batch B2C disbursement")
    @Audit(action = AuditEventType.PAYMENT_INITIATED, resource = "BULK_B2C_CSV")
    public ResponseEntity<ApiResponse<BulkPayoutResultDto>> processCsvBulkPayouts(
            @RequestParam("file") MultipartFile file) {
        log.info("Received request to process CSV bulk B2C payout file: {}, size: {} bytes",
                file.getOriginalFilename(), file.getSize());

        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded CSV file cannot be empty");
        }

        try (InputStream inputStream = file.getInputStream()) {
            BulkPayoutResultDto result = bulkPayoutService.processCsvBulkPayouts(inputStream);
            return ResponseEntity.ok(ApiResponse.success(result, "Bulk B2C payout CSV file processed successfully"));
        } catch (Exception e) {
            log.error("Error processing CSV bulk payout file: {}", e.getMessage(), e);
            throw new IllegalArgumentException("Failed to process bulk payout CSV file: " + e.getMessage(), e);
        }
    }
}
