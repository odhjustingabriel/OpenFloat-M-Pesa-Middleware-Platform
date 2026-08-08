package com.openfloat.mpesa.controller;

import com.openfloat.mpesa.common.dto.ApiResponse;
import com.openfloat.mpesa.common.dto.PagedResponse;
import com.openfloat.mpesa.dto.CreateInvoiceRequestDto;
import com.openfloat.mpesa.dto.InvoiceResponseDto;
import com.openfloat.mpesa.entity.enums.InvoiceStatus;
import com.openfloat.mpesa.service.InvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Controller for managing customer invoices and inspecting payment fulfillment status.
 *
 * <p>Phase 9 — Component 3: Invoicing Engine & Payment Fulfillment</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/invoices")
@RequiredArgsConstructor
@Tag(name = "Invoice Management", description = "Create, view, filter, and cancel customer invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Create a new customer invoice linked to an Account Reference")
    public ResponseEntity<ApiResponse<InvoiceResponseDto>> createInvoice(@Valid @RequestBody CreateInvoiceRequestDto dto) {
        log.info("Request received to create invoice for customer: {}", dto.getCustomerPhone());
        InvoiceResponseDto response = invoiceService.createInvoice(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Invoice created successfully"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR', 'FINANCE', 'VIEWER')")
    @Operation(summary = "Get detailed information for a single invoice")
    public ResponseEntity<ApiResponse<InvoiceResponseDto>> getInvoice(@PathVariable UUID id) {
        log.info("Fetching invoice details for ID: {}", id);
        InvoiceResponseDto response = invoiceService.getInvoice(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR', 'FINANCE', 'VIEWER')")
    @Operation(summary = "Search and filter customer invoices with pagination")
    public ResponseEntity<ApiResponse<PagedResponse<InvoiceResponseDto>>> searchInvoices(
            @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(required = false) String customerPhone,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("Searching invoices: status={}, phone={}, dueDateFrom={}, dueDateTo={}", status, customerPhone, dueDateFrom, dueDateTo);
        Page<InvoiceResponseDto> resultPage = invoiceService.searchInvoices(status, customerPhone, dueDateFrom, dueDateTo, page, size);

        PagedResponse<InvoiceResponseDto> pagedResponse = PagedResponse.of(
                resultPage.getContent(),
                resultPage.getNumber(),
                resultPage.getSize(),
                resultPage.getTotalElements(),
                resultPage.getTotalPages()
        );

        return ResponseEntity.ok(ApiResponse.success(pagedResponse));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Cancel an unpaid or partial invoice")
    public ResponseEntity<ApiResponse<InvoiceResponseDto>> cancelInvoice(@PathVariable UUID id) {
        log.info("Request received to cancel invoice ID: {}", id);
        InvoiceResponseDto response = invoiceService.cancelInvoice(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Invoice cancelled successfully"));
    }
}
