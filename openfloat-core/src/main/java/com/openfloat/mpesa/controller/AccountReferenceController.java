package com.openfloat.mpesa.controller;

import com.openfloat.mpesa.common.dto.ApiResponse;
import com.openfloat.mpesa.common.exception.ResourceNotFoundException;
import com.openfloat.mpesa.dto.AccountRefGenerateRequestDto;
import com.openfloat.mpesa.dto.AccountRefResponseDto;
import com.openfloat.mpesa.entity.AccountReferenceMapping;
import com.openfloat.mpesa.service.AccountReferenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller for generating and inspecting dynamic Account References.
 * Accessible to ADMIN and MANAGER roles, and registered client applications via API credentials.
 */
@RestController
@RequestMapping("/api/v1/references")
@RequiredArgsConstructor
public class AccountReferenceController {

    private final AccountReferenceService referenceService;

    /**
     * Generate a unique Account Reference (ECOMM-8X92K4) linked to a client application.
     */
    @PostMapping("/generate")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    public ResponseEntity<ApiResponse<AccountRefResponseDto>> generateReference(@Valid @RequestBody AccountRefGenerateRequestDto dto) {
        AccountRefResponseDto response = referenceService.generateReference(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response, "Account Reference generated successfully"));
    }

    /**
     * Lookup mapping details for an account reference string.
     */
    @GetMapping("/{reference}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR', 'FINANCE', 'VIEWER')")
    public ResponseEntity<ApiResponse<AccountRefResponseDto>> getReferenceDetails(@PathVariable String reference) {
        AccountReferenceMapping mapping = referenceService.findByAccountReference(reference)
                .orElseThrow(() -> new ResourceNotFoundException("Account reference not found: " + reference));

        return ResponseEntity.ok(ApiResponse.success(AccountRefResponseDto.builder()
                .id(mapping.getId())
                .accountReference(mapping.getAccountReference())
                .clientAppId(mapping.getClientApp().getId())
                .clientAppName(mapping.getClientApp().getClientName())
                .accountPrefix(mapping.getClientApp().getAccountPrefix())
                .callbackUrl(mapping.getCallbackUrl())
                .requestedAmount(mapping.getRequestedAmount())
                .currency(mapping.getCurrency())
                .description(mapping.getDescription())
                .status(mapping.getStatus())
                .expiresAt(mapping.getExpiresAt())
                .paidAt(mapping.getPaidAt())
                .transactionId(mapping.getTransaction() != null ? mapping.getTransaction().getId().toString() : null)
                .createdAt(mapping.getCreatedAt())
                .build()));
    }

    /**
     * List all generated references for a client application.
     */
    @GetMapping("/client/{clientAppId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<AccountRefResponseDto>>> getClientReferences(@PathVariable UUID clientAppId) {
        return ResponseEntity.ok(ApiResponse.success(referenceService.getReferencesByClientAppId(clientAppId)));
    }
}
