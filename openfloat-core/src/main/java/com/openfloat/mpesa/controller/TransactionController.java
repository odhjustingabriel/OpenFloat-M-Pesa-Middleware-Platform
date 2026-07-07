package com.openfloat.mpesa.controller;

import com.openfloat.mpesa.common.dto.ApiResponse;
import com.openfloat.mpesa.common.dto.PagedResponse;
import com.openfloat.mpesa.dto.TransactionDto;
import com.openfloat.mpesa.dto.TransactionSearchCriteria;
import com.openfloat.mpesa.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Tag(name = "Transaction Management", description = "Query and search M-Pesa transactions")
public class TransactionController {

    private final TransactionService transactionService;

    @GetMapping("/{id}")
    @Operation(summary = "Get detailed information for a single transaction including callback payload")
    public ResponseEntity<ApiResponse<TransactionDto>> getTransaction(@PathVariable UUID id) {
        log.info("Fetching transaction details for ID: {}", id);
        TransactionDto transaction = transactionService.getTransaction(id);
        return ResponseEntity.ok(ApiResponse.success(transaction));
    }

    @GetMapping
    @Operation(summary = "Search and filter transactions with pagination and sorting")
    public ResponseEntity<ApiResponse<PagedResponse<TransactionDto>>> searchTransactions(
            @ModelAttribute TransactionSearchCriteria criteria) {
        log.info("Searching transactions with criteria: {}", criteria);
        Page<TransactionDto> page = transactionService.searchTransactions(criteria);
        
        PagedResponse<TransactionDto> pagedResponse = PagedResponse.of(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );

        return ResponseEntity.ok(ApiResponse.success(pagedResponse));
    }
}
