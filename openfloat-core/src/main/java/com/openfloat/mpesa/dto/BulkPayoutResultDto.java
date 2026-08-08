package com.openfloat.mpesa.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Summary DTO returned after executing a bulk B2C payout batch.
 *
 * <p>Phase 9 — Component 4: Bulk Payouts & Batch B2C Disbursement Engine</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkPayoutResultDto {

    private String batchId;
    private int totalRecords;
    private int successfulCount;
    private int failedCount;
    private BigDecimal totalAmount;
    private Instant processedAt;
    private List<ItemResult> itemResults;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemResult {
        private int rowNumber;
        private String phoneNumber;
        private BigDecimal amount;
        private boolean success;
        private UUID transactionId;
        private String errorMessage;
    }
}
